import com.sun.source.util.JavacTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// Parse overlays without Android dependencies; the CI Gradle step checks types and APIs.
public final class CheckJavaSyntax {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A JDK is required");
        Path root = Path.of(args.length == 0 ? "patches/concept5/java" : args[0]);
        List<Path> paths;
        try (var files = Files.walk(root)) {
            paths = files.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
        if (paths.isEmpty()) throw new IllegalStateException("No Java overlays found: " + root);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    Arrays.asList("-proc:none", "-source", "8", "-encoding", "UTF-8"),
                    null, manager.getJavaFileObjectsFromPaths(paths));
            task.parse();
        }
        boolean failed = false;
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                System.err.println(diagnostic);
                failed = true;
            }
        }
        if (failed) System.exit(1);
        System.out.println("Java syntax OK: " + paths.size() + " overlay files");
    }
}
