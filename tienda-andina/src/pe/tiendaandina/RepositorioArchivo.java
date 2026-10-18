package pe.tiendaandina;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import static pe.tiendaandina.Dominio.*;

/** Snapshot local versionado. No importar archivos recibidos de terceros. */
public final class RepositorioArchivo {
    public record Datos(int version, GestorPedidos gestor, List<Producto> productos, Map<String, Cupon> cupones) implements Serializable {
        public Datos(GestorPedidos gestor, List<Producto> productos, Map<String, Cupon> cupones) {
            this(1, gestor, List.copyOf(productos), Map.copyOf(cupones));
        }
    }
    private final Path archivo;
    public RepositorioArchivo(Path archivo) { this.archivo = archivo.toAbsolutePath(); }
    public boolean existe() { return Files.exists(archivo); }
    public Datos cargar(PasarelaCobro pasarela) throws IOException {
        try (ObjectInputStream entrada = new ObjectInputStream(Files.newInputStream(archivo))) {
            entrada.setObjectInputFilter(info -> {
                if (info.depth() > 80 || info.references() > 200_000 || info.streamBytes() > 32_000_000 || info.arrayLength() > 200_000)
                    return ObjectInputFilter.Status.REJECTED;
                Class<?> tipo = info.serialClass();
                if (tipo == null) return ObjectInputFilter.Status.UNDECIDED;
                while (tipo.isArray()) tipo = tipo.getComponentType();
                String nombre = tipo.getName();
                return tipo.isPrimitive() || nombre.startsWith("pe.tiendaandina.") || nombre.startsWith("java.util.")
                        || nombre.startsWith("java.lang.") || nombre.startsWith("java.math.") || nombre.startsWith("java.time.")
                        ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
            });
            Object objeto = entrada.readObject();
            if (!(objeto instanceof Datos datos) || datos.version() != 1 || datos.gestor() == null
                    || datos.productos() == null || datos.cupones() == null) throw new IOException("Formato de datos incompatible");
            datos.gestor().conectar(pasarela);
            return datos;
        } catch (ClassNotFoundException | RuntimeException e) { throw new IOException("No se pudieron recuperar los datos", e); }
    }
    public void guardar(Datos datos) throws IOException {
        Files.createDirectories(archivo.getParent());
        Path temporal = Files.createTempFile(archivo.getParent(), ".tienda-", ".tmp");
        try {
            try (FileOutputStream salida = new FileOutputStream(temporal.toFile()); ObjectOutputStream objetos = new ObjectOutputStream(salida)) {
                objetos.writeObject(datos); objetos.flush(); salida.getFD().sync();
            }
            // No se degrada a una sustitución no atómica si el sistema no lo soporta.
            Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporal); }
    }
    /** Una sola instancia de la demo puede escribir el snapshot a la vez. */
    public AutoCloseable bloquear() throws IOException {
        Files.createDirectories(archivo.getParent());
        FileChannel canal = FileChannel.open(archivo.resolveSibling(archivo.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = canal.tryLock();
            if (lock == null) throw new IOException("La aplicación ya está abierta en otra instancia");
            return () -> { lock.release(); canal.close(); };
        } catch (IOException | RuntimeException e) { canal.close(); throw e; }
    }
}
