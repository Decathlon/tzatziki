import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public interface HTMLElement {
    String getId();
    List<String> getClasses();
    Map<String, Object> getAttributes();
    default <T> T getAttribute(String attribute) {
        return (T) getAttributes().get(attribute);
    };
    default Map<String, String> getStyle() {
        return getAttribute("style");
    };
}
