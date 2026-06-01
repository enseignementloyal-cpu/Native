package com.lotato.printer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.IBinder;
import android.os.RemoteException;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import woyou.aidlservice.jiuiv5.IWoyouService;

@CapacitorPlugin(name = "ThermalPrinter")
public class ThermalPrinterPlugin extends Plugin {

    private IWoyouService sunmiPrinterService = null;
    private boolean sunmiConnected = false;

    private ServiceConnection sunmiConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            sunmiPrinterService = IWoyouService.Stub.asInterface(service);
            sunmiConnected = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            sunmiPrinterService = null;
            sunmiConnected = false;
        }
    };

    @Override
    public void load() {
        connectSunmiService();
    }

    private void connectSunmiService() {
        try {
            Intent intent = new Intent();
            intent.setPackage("woyou.aidlservice.jiuiv5");
            intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
            getContext().bindService(intent, sunmiConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            sunmiConnected = false;
        }
    }

    @PluginMethod
    public void print(PluginCall call) {
        String html = call.getString("html", "");
        if (html.isEmpty()) { call.reject("HTML vide"); return; }
        if (sunmiConnected && sunmiPrinterService != null) {
            printViaSunmi(call, html);
        } else {
            printViaAndroidPrintManager(call, html);
        }
    }

    private void printViaSunmi(PluginCall call, String html) {
        getActivity().runOnUiThread(() -> {
            try {
                WebView offscreen = new WebView(getContext());
                int widthPx = (int)(getContext().getResources().getDisplayMetrics().density * 384);
                offscreen.layout(0, 0, widthPx, 1);
                offscreen.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                offscreen.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        view.postDelayed(() -> {
                            try {
                                int measuredHeight = view.getContentHeight();
                                view.layout(0, 0, widthPx, measuredHeight);
                                Bitmap bitmap = Bitmap.createBitmap(widthPx, measuredHeight, Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(bitmap);
                                canvas.drawColor(Color.WHITE);
                                view.draw(canvas);
                                sunmiPrinterService.printerInit(null);
                                sunmiPrinterService.printBitmap(bitmap, null);
                                sunmiPrinterService.printText("\n\n\n", null);
                                sunmiPrinterService.cutPaper(null);
                                JSObject ret = new JSObject();
                                ret.put("method", "sunmi");
                                ret.put("success", true);
                                call.resolve(ret);
                            } catch (RemoteException e) {
                                call.reject("Erreur Sunmi: " + e.getMessage());
                            }
                        }, 800);
                    }
                });
            } catch (Exception e) {
                printViaAndroidPrintManager(call, html);
            }
        });
    }

    private void printViaAndroidPrintManager(PluginCall call, String html) {
        getActivity().runOnUiThread(() -> {
            try {
                WebView webView = new WebView(getContext());
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager printManager = (PrintManager)
                            getActivity().getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter printAdapter =
                            view.createPrintDocumentAdapter("Ticket LOTATO");
                        PrintAttributes.Builder builder = new PrintAttributes.Builder();
                        builder.setMediaSize(PrintAttributes.MediaSize.ISO_A6);
                        builder.setResolution(new PrintAttributes.Resolution("lotato", "Ticket", 203, 203));
                        builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS);
                        PrintJob printJob = printManager.print("Ticket LOTATO", printAdapter, builder.build());
                        JSObject ret = new JSObject();
                        ret.put("method", "android_print_manager");
                        ret.put("success", true);
                        ret.put("jobId", printJob.getId().toString());
                        call.resolve(ret);
                    }
                });
            } catch (Exception e) {
                call.reject("Erreur impression: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void getCapabilities(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("sunmi", sunmiConnected);
        ret.put("androidPrintManager", true);
        ret.put("deviceModel", android.os.Build.MODEL);
        ret.put("manufacturer", android.os.Build.MANUFACTURER);
        call.resolve(ret);
    }
}
