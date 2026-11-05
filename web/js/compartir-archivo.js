// compartir-archivo.js — entregar un archivo que arma la app: menú «Compartir» del
// teléfono (navigator.share) o, si no hay, una descarga.

function descargar(nombre, tipo, bytes) {
  const url = URL.createObjectURL(new Blob([bytes], { type: tipo }));
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

/**
 * @param {{nombre: string, tipo: string, bytes: Uint8Array, titulo: string}} archivo
 * @returns {Promise<'compartido'|'descargado'|'cancelado'>}
 */
export async function compartirArchivo({ nombre, tipo, bytes, titulo }) {
  if (typeof File !== 'undefined' && navigator.canShare && matchMedia('(pointer: coarse)').matches) {
    const archivo = new File([bytes], nombre, { type: tipo });
    if (navigator.canShare({ files: [archivo] })) {
      try {
        await navigator.share({ files: [archivo], title: titulo });
        return 'compartido';
      } catch (e) {
        if (e && e.name === 'AbortError') return 'cancelado';
      }
    }
  }
  descargar(nombre, tipo, bytes);
  return 'descargado';
}
