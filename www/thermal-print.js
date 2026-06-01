/**
 * thermal-print.js — LOTATO PRO
 * Remplace window.AndroidPrint par le vrai plugin ThermalPrinter Capacitor.
 * Fonctionne sur : Sunmi V2s/T2s/V1s, Telpo, Urovo, Android standard.
 * Inclure dans agent1.html AVANT config.js
 */
(function () {
  'use strict';

  const IS_CAPACITOR = !!(window.Capacitor && window.Capacitor.isNativePlatform());

  const ThermalPrint = {

    async printHTML(html) {
      if (IS_CAPACITOR) {
        return await ThermalPrint._printNative(html);
      } else {
        return ThermalPrint._printBrowser(html);
      }
    },

    async _printNative(html) {
      try {
        const { ThermalPrinter } = Capacitor.Plugins;
        if (!ThermalPrinter) throw new Error('Plugin ThermalPrinter non trouvé');
        const result = await ThermalPrinter.print({ html });
        console.log('Impression OK —', result.method);
        return result;
      } catch (err) {
        console.error('Erreur impression native:', err);
        ThermalPrint._printShare(html);
        throw err;
      }
    },

    _printBrowser(html) {
      const win = window.open('', '_blank', 'width=500,height=750');
      if (!win) { alert('Autorize popups pou enprime.'); return; }
      win.document.write(html);
      win.document.close();
      win.onload = () => { win.focus(); win.print(); };
    },

    async _printShare(html) {
      try {
        const blob = new Blob([html], { type: 'text/html' });
        const file = new File([blob], 'ticket-lotato.html', { type: 'text/html' });
        if (navigator.share && navigator.canShare({ files: [file] })) {
          await navigator.share({ title: 'Ticket LOTATO', files: [file] });
        } else {
          const url = URL.createObjectURL(blob);
          window.open(url, '_blank');
        }
      } catch (e) {
        console.error('Partage échoué:', e);
      }
    },

    async getCapabilities() {
      if (!IS_CAPACITOR) {
        return { sunmi: false, androidPrintManager: false, deviceModel: 'Browser' };
      }
      const { ThermalPrinter } = Capacitor.Plugins;
      return await ThermalPrinter.getCapabilities();
    }
  };

  // Compatibilité totale avec cartManager.js existant
  window.AndroidPrint = {
    printHTML: (html) => ThermalPrint.printHTML(html)
  };

  // Dans l'APK, isAndroidWebView() retourne toujours true
  if (IS_CAPACITOR) {
    window.isAndroidWebView = function () { return true; };
  }

  window.ThermalPrint = ThermalPrint;
  console.log('🖨 ThermalPrint — ' + (IS_CAPACITOR ? 'Mode APK natif' : 'Mode navigateur'));
})();
