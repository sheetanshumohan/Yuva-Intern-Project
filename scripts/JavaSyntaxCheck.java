import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Java 21 source parser with JDK compiler frontend; no Spring dependencies needed.
 * Checks grammar/syntax, NOT dependency resolution, semantics, or runtime behavior.
 */
public final class JavaSyntaxCheck {
  public static void main(String[] args) throws IOException {
    Path root=Path.of(args.length>0?args[0]:".").toAbsolutePath();
    List<Path> sources;
    try(var stream=Files.walk(root.resolve("src"))) {
      sources=stream.filter(p->p.toString().endsWith(".java")).sorted().toList();
    }
    if(sources.isEmpty()) throw new AssertionError("No Java sources found");
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    if(compiler==null) throw new IllegalStateException("JDK (not JRE) required");
    DiagnosticCollector<JavaFileObject> report=new DiagnosticCollector<>();
    try(StandardJavaFileManager fm=compiler.getStandardFileManager(report,Locale.ROOT,null)) {
      var units=fm.getJavaFileObjectsFromPaths(sources);
      JavacTask task=(JavacTask)compiler.getTask(null,fm,report,List.of("-proc:none","--release","21"),null,units);
      task.parse();
    }
    var errors=report.getDiagnostics().stream().filter(x->x.getKind()==Diagnostic.Kind.ERROR).toList();
    if(!errors.isEmpty()) {
      errors.forEach(System.err::println);
      throw new AssertionError(errors.size()+" Java syntax errors");
    }
    System.out.println("PASS: JDK javac parsed "+sources.size()+" Java source/test files without syntax errors");
  }
}
