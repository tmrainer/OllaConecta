// Script clásico cargado en el <head>: aplica tema, letra y botón de voz antes del
// primer cuadro, para que no se vea primero el aspecto por defecto. Ajustes vuelve a
// llamar a window.aplicarPreferencias() tras cada cambio. Son del teléfono, no de la
// olla: no van en las copias.
//
//   pi3.tema         'auto' | 'claro' | 'oscuro'      (auto: el del teléfono)
//   pi3.letra        'normal' | 'grande' | 'muy-grande'
//   pi3.voz.boton    '1' para mostrar 🔊 en la cabecera
//   pi3.voz.velocidad 'normal' | 'lenta'

(function () {
  'use strict';

  function leer(clave, porDefecto) {
    try { return localStorage.getItem(clave) || porDefecto; } catch (e) { return porDefecto; }
  }

  window.aplicarPreferencias = function () {
    var raiz = document.documentElement;
    var tema = leer('pi3.tema', 'auto');
    if (tema === 'claro' || tema === 'oscuro') raiz.setAttribute('data-tema', tema);
    else raiz.removeAttribute('data-tema');

    // «Letra grande» de antes (pi3.letraGrande = '1') sigue valiendo.
    var letra = leer('pi3.letra', leer('pi3.letraGrande', '0') === '1' ? 'grande' : 'normal');
    raiz.classList.toggle('letra-grande', letra === 'grande');
    raiz.classList.toggle('letra-muy-grande', letra === 'muy-grande');

    raiz.classList.toggle('con-voz', leer('pi3.voz.boton', '0') === '1');
  };

  window.aplicarPreferencias();
})();
