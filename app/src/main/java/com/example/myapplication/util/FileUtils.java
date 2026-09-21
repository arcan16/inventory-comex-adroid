package com.example.myapplication.util;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Helpers para leer un archivo elegido con el selector de documentos
 * (content:// URI) sin depender de una ruta de archivo real, que en Android
 * moderno no siempre existe (Storage Access Framework / proveedores en la nube).
 */
public final class FileUtils {

    private FileUtils() {
    }

    /** Nombre del archivo tal como lo reporta el proveedor de contenido; null si no se pudo leer. */
    public static String getDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        return null;
    }

    /** Tamano en bytes tal como lo reporta el proveedor de contenido; -1 si no esta disponible. */
    public static long getSize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        }
        return -1;
    }

    /** Lee el contenido completo del URI. Debe llamarse fuera del hilo principal. */
    public static byte[] readAllBytes(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("No se pudo abrir el archivo seleccionado");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }
}
