package com.cloudrail.fileviewer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.cloudrail.si.exceptions.ParseException;
import com.cloudrail.si.services.Box;
import com.cloudrail.si.interfaces.CloudStorage;
import com.cloudrail.si.services.Dropbox;
import com.cloudrail.si.services.GoogleDrive;
import com.cloudrail.si.services.OneDrive;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by patrick on 08.04.16.
 */
public class Services {
    private static Services ourInstance = new Services();

    private final AtomicReference<CloudStorage> dropbox = new AtomicReference<>();
    private final AtomicReference<CloudStorage> box = new AtomicReference<>();
    private final AtomicReference<CloudStorage> googledrive = new AtomicReference<>();
    private final AtomicReference<CloudStorage> onedrive = new AtomicReference<>();

    private Activity context = null;

    public static Services getInstance() {
        return ourInstance;
    }

    private Services() {
    }

    private void initDropbox() {
        dropbox.set(new Dropbox(context, "u4gevj9clhvdjug", "9ol49hdlk8by9v9"));
    }

    private void initBox() {
        box.set(new Box(context, "zqgl7zrzxei2c076ss5k9hxf2ivbppfa", "ueG5uWHUarWYQNgldCsCwUwGzvSWlR0Y"));
    }

    private void initGoogleDrive() {
        googledrive.set(new GoogleDrive(context, "638240013795-k6cavk4npp6gtqkpb56icpm0hm4uo6aq.apps.googleusercontent.com", "hhJG6zCn4F7ObJUzllL3BXoL"));
    }

    private void initOneDrive() {
        onedrive.set(new OneDrive(context, "000000004018F12F", "lGQPubehDO6eklir1GQmIuCPFfzwihMo"));
    }

    // --------- Public Methods -----------
    public void prepare(Activity context) {
        this.context = context;

        this.initDropbox();
        this.initBox();
        this.initGoogleDrive();
        this.initOneDrive();

        SharedPreferences sharedPreferences = context.getPreferences(Context.MODE_PRIVATE);

        try {
            String persistent = sharedPreferences.getString("dropboxPersistent", null);
            if (persistent != null) dropbox.get().loadAsString(persistent);
            persistent = sharedPreferences.getString("boxPersistent", null);
            if (persistent != null) box.get().loadAsString(persistent);
            persistent = sharedPreferences.getString("googledrivePersistent", null);
            if (persistent != null) googledrive.get().loadAsString(persistent);
            persistent = sharedPreferences.getString("onedrivePersistent", null);
            if (persistent != null) onedrive.get().loadAsString(persistent);
        } catch (ParseException e) {}
    }

    public CloudStorage getService(int service) {
        AtomicReference<CloudStorage> ret = new AtomicReference<>();

        switch (service) {
            case 1:
                ret = this.dropbox;
                break;
            case 2:
                ret = this.box;
                break;
            case 3:
                ret = this.googledrive;
                break;
            case 4:
                ret = this.onedrive;
                break;
            default:
                throw new IllegalArgumentException("Unknown service!");
        }

        return ret.get();
    }

    public void storePersistent() {
        SharedPreferences sharedPreferences = context.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("dropboxPersistent", dropbox.get().saveAsString());
        editor.putString("boxPersistent", box.get().saveAsString());
        editor.putString("googledrivePersistent", googledrive.get().saveAsString());
        editor.putString("onedrivePersistent", onedrive.get().saveAsString());
        editor.commit();
    }
}
