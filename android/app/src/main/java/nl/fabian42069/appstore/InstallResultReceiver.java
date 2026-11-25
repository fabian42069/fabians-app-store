package nl.fabian42069.appstore;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Receives Android's answer to an install: ask the user to confirm, or report the result. */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
        String pkg = intent.getStringExtra("pkg");
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // Android shows its own "Do you want to install this app?" screen.
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                MainActivity.installFinished(pkg, true, "");
                break;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                MainActivity.installFinished(pkg, false, "Installation cancelled.");
                break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                MainActivity.installFinished(pkg, false, "This app is already installed from somewhere else with a different signature. Uninstall it first, then install it here.");
                break;
            default:
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                MainActivity.installFinished(pkg, false, msg != null ? msg : "Installation failed.");
        }
    }
}
