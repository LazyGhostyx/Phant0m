package xyz.lazyghosty.phant0m.server.shell;

import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;

import frb.phant0m.api.Phant0m;
import frb.phant0m.shared.Phant0mApiConstant;
import rikka.rish.Rish;
import rikka.rish.RishConfig;

public class Shell extends Rish {

    public static void main(String[] args, String packageName, IBinder binder, Handler handler) {
        RishConfig.init(binder, Phant0mApiConstant.server.BINDER_DESCRIPTOR, 30000);
        Phant0m.onBinderReceived(binder, packageName);
        Phant0m.addBinderReceivedListenerSticky(() -> {
            handler.post(() -> new Shell().start(args));
        });
    }

    @Override
    public void requestPermission(Runnable onGrantedRunnable) {
        if (Phant0m.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            onGrantedRunnable.run();
        } else if (Phant0m.shouldShowRequestPermissionRationale()) {
            System.err.println("Permission denied");
            System.err.flush();
            System.exit(1);
        } else {
            Phant0m.addRequestPermissionResultListener(new Phant0m.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    Phant0m.removeRequestPermissionResultListener(this);

                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        onGrantedRunnable.run();
                    } else {
                        System.err.println("Permission denied");
                        System.err.flush();
                        System.exit(1);
                    }
                }
            });
            Phant0m.requestPermission(0);
        }
    }
}
