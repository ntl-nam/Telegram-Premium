package org.telegram.messenger.telepremium;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local TelePremium transcription bridge for downloaded Telegram voice/audio
 * messages. Android 13+ can feed an already-opened PCM audio source directly
 * into the system RecognitionService via RecognizerIntent.EXTRA_AUDIO_SOURCE.
 *
 * This deliberately does not touch Telegram Premium/account state and does not
 * call messages.transcribeAudio.
 */
public final class TelePremiumLocalTranscriber {

    public interface Callback {
        void onSuccess(String text);
        void onError(String reason);
    }

    private static final long CODEC_TIMEOUT_US = 10_000L;

    private TelePremiumLocalTranscriber() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && SpeechRecognizer.isRecognitionAvailable(ApplicationLoader.applicationContext);
    }

    public static void transcribe(MessageObject messageObject, Callback callback) {
        if (messageObject == null || messageObject.messageOwner == null) {
            postError(callback, "TelePremium: голосовое сообщение недоступно");
            return;
        }
        if (!isSupported()) {
            postError(callback, "TelePremium: локальная расшифровка требует Android 13+ и системный Speech Recognizer");
            return;
        }

        final File source = FileLoader.getInstance(messageObject.currentAccount)
                .getPathToMessage(messageObject.messageOwner);
        if (source == null || !source.exists() || source.length() <= 0) {
            postError(callback, "TelePremium: сначала загрузите голосовое сообщение");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                DecodedAudio decoded = decodeToPcm(source, messageObject.currentAccount, messageObject.getId());
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        new RecognitionSession(decoded, callback).start();
                    } catch (Throwable e) {
                        FileLog.e(e);
                        decoded.file.delete();
                        postError(callback, "TelePremium: не удалось запустить распознавание");
                    }
                });
            } catch (Throwable e) {
                FileLog.e(e);
                postError(callback, "TelePremium: не удалось декодировать голосовое сообщение");
            }
        });
    }

    private static void postError(Callback callback, String reason) {
        if (callback == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> callback.onError(reason));
    }

    private static DecodedAudio decodeToPcm(File source, int account, int messageId) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        FileOutputStream output = null;
        File pcm = new File(
                ApplicationLoader.applicationContext.getCacheDir(),
                "telepremium_stt_" + account + "_" + messageId + "_" + System.nanoTime() + ".pcm"
        );

        int sampleRate = 16000;
        int channelCount = 1;
        int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

        try {
            extractor.setDataSource(source.getAbsolutePath());

            MediaFormat inputFormat = null;
            int trackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    inputFormat = format;
                    break;
                }
            }
            if (trackIndex < 0 || inputFormat == null) {
                throw new IllegalStateException("No audio track");
            }

            if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            }
            if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }

            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (TextUtils.isEmpty(mime)) {
                throw new IllegalStateException("No audio MIME");
            }

            extractor.selectTrack(trackIndex);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            output = new FileOutputStream(pcm, false);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) {
                            throw new IllegalStateException("Decoder input buffer unavailable");
                        }
                        inputBuffer.clear();
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    Math.max(0, extractor.getSampleTime()),
                                    0
                            );
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        ByteBuffer copy = outputBuffer.duplicate();
                        copy.position(info.offset);
                        copy.limit(info.offset + info.size);
                        byte[] chunk = new byte[info.size];
                        copy.get(chunk);
                        output.write(chunk);
                    }
                    decoder.releaseOutputBuffer(outputIndex, false);
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = decoder.getOutputFormat();
                    if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                            && outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                }
            }

            output.flush();
            if (!pcm.exists() || pcm.length() <= 0) {
                throw new IllegalStateException("Decoded PCM is empty");
            }
            return new DecodedAudio(pcm, sampleRate, channelCount, pcmEncoding);
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignore) {
                }
            }
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Throwable ignore) {
                }
                try {
                    decoder.release();
                } catch (Throwable ignore) {
                }
            }
            try {
                extractor.release();
            } catch (Throwable ignore) {
            }
        }
    }

    private static final class DecodedAudio {
        final File file;
        final int sampleRate;
        final int channelCount;
        final int encoding;

        DecodedAudio(File file, int sampleRate, int channelCount, int encoding) {
            this.file = file;
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.encoding = encoding;
        }
    }

    private static final class RecognitionSession implements RecognitionListener {
        private final DecodedAudio audio;
        private final Callback callback;
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final StringBuilder segmentedText = new StringBuilder();

        private SpeechRecognizer recognizer;
        private ParcelFileDescriptor descriptor;

        RecognitionSession(DecodedAudio audio, Callback callback) {
            this.audio = audio;
            this.callback = callback;
        }

        void start() throws Exception {
            descriptor = ParcelFileDescriptor.open(audio.file, ParcelFileDescriptor.MODE_READ_ONLY);
            recognizer = SpeechRecognizer.createSpeechRecognizer(ApplicationLoader.applicationContext);
            recognizer.setRecognitionListener(this);

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, audio.channelCount);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, audio.encoding);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, audio.sampleRate);
            intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);

            recognizer.startListening(intent);
        }

        private String best(Bundle bundle) {
            if (bundle == null) {
                return null;
            }
            ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (results == null || results.isEmpty()) {
                return null;
            }
            return results.get(0);
        }

        private void appendSegment(String text) {
            if (TextUtils.isEmpty(text)) {
                return;
            }
            String value = text.trim();
            if (value.isEmpty()) {
                return;
            }
            if (segmentedText.length() > 0) {
                segmentedText.append(' ');
            }
            segmentedText.append(value);
        }

        private void success(String text) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            cleanup();
            if (callback != null) {
                callback.onSuccess(text == null ? "" : text.trim());
            }
        }

        private void error(int code) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            cleanup();
            if (callback != null) {
                callback.onError("TelePremium: ошибка распознавания (" + code + ")");
            }
        }

        private void cleanup() {
            if (recognizer != null) {
                try {
                    recognizer.destroy();
                } catch (Throwable ignore) {
                }
                recognizer = null;
            }
            if (descriptor != null) {
                try {
                    descriptor.close();
                } catch (Throwable ignore) {
                }
                descriptor = null;
            }
            if (audio.file != null) {
                try {
                    audio.file.delete();
                } catch (Throwable ignore) {
                }
            }
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            error(error);
        }

        @Override
        public void onResults(Bundle results) {
            String result = best(results);
            if (segmentedText.length() > 0) {
                if (!TextUtils.isEmpty(result) && !segmentedText.toString().endsWith(result.trim())) {
                    appendSegment(result);
                }
                success(segmentedText.toString());
            } else if (!TextUtils.isEmpty(result)) {
                success(result);
            } else {
                error(SpeechRecognizer.ERROR_NO_MATCH);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }

        public void onSegmentResults(Bundle segmentResults) {
            appendSegment(best(segmentResults));
        }

        public void onEndOfSegmentedSession() {
            if (segmentedText.length() > 0) {
                success(segmentedText.toString());
            } else {
                error(SpeechRecognizer.ERROR_NO_MATCH);
            }
        }
    }
}
