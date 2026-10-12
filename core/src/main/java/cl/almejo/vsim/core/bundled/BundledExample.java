package cl.almejo.vsim.core.bundled;

/** One classic circuit shipped inside VSim (M6.1-A): its id and the classpath resource that holds its file. */
public record BundledExample(String id, String resourceName) {
}
