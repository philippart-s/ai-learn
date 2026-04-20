import dev.langchain4j.service.TokenStream;

@FunctionalInterface
public interface ChatFunction {
  TokenStream chat(String message);
}
