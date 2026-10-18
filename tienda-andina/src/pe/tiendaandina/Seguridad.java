package pe.tiendaandina;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Autenticación local de demostración; nunca guarda contraseñas en texto plano. */
public final class Seguridad {
    private Seguridad() {}
    public enum Rol { CLIENTE, OPERADOR, ADMINISTRADOR }
    public record Sesion(String usuario, Rol rol, String clienteId) {
        public Sesion { Objects.requireNonNull(usuario); Objects.requireNonNull(rol); }
        public void exigir(Rol esperado) {
            if (rol != esperado) throw new SecurityException("Esta operación requiere rol " + esperado);
        }
    }
    public static final class Autenticacion {
        private record Cuenta(Usuario usuario, byte[] sal, byte[] hash) {}
        private final Map<String, Cuenta> cuentas = new HashMap<>();
        public void registrar(String usuario, char[] clave, Rol rol, String clienteId) {
            Objects.requireNonNull(rol);
            Usuario identidad = switch (rol) {
                case CLIENTE -> new Dominio.Cliente(clienteId, usuario, Dominio.TipoCliente.REGULAR, "Sin especificar", "");
                case OPERADOR -> new Operador(usuario, usuario);
                case ADMINISTRADOR -> new Administrador(usuario, usuario);
            };
            registrar(usuario, clave, identidad);
        }
        public void registrar(String usuario, char[] clave, Usuario identidad) {
            if (usuario == null || usuario.isBlank() || clave.length < 8 || cuentas.containsKey(usuario))
                throw new IllegalArgumentException("Usuario duplicado o credenciales inválidas (mínimo 8 caracteres)");
            Objects.requireNonNull(identidad);
            byte[] sal = new byte[16]; new SecureRandom().nextBytes(sal);
            cuentas.put(usuario, new Cuenta(identidad, sal, derivar(clave, sal)));
        }
        public Sesion entrar(String usuario, char[] clave) {
            Cuenta c = cuentas.get(usuario);
            byte[] sal = c == null ? new byte[16] : c.sal;
            byte[] intento = derivar(clave, sal);
            if (c == null || !MessageDigest.isEqual(c.hash, intento)) throw new SecurityException("Usuario o contraseña incorrectos");
            return new Sesion(usuario, c.usuario.rol(), c.usuario.clienteId());
        }
        private static byte[] derivar(char[] clave, byte[] sal) {
            PBEKeySpec especificacion = new PBEKeySpec(clave, sal, 120_000, 256);
            try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).getEncoded(); }
            catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("No hay soporte criptográfico", e); }
            finally { especificacion.clearPassword(); }
        }
    }
}
