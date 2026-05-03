import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.zip.ZipEntry;

public class ReadZip {
    public static void main(String[] args) throws Exception {
        ZipFile zip = new ZipFile("C:\\Users\\le4tk\\.gradle\\caches\\fabric-loom\\minecraftMaven\\net\\minecraft\\minecraft-merged\\1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2\\minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar");
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.contains("PacketCodec") && !name.contains("$")) System.out.println(name);
            if (name.contains("StreamCodec") && !name.contains("$")) System.out.println(name);
            if (name.contains("RegistryByteBuf") && !name.contains("$")) System.out.println(name);
            if (name.contains("RegistryFriendlyByteBuf") && !name.contains("$")) System.out.println(name);
        }
    }
}
