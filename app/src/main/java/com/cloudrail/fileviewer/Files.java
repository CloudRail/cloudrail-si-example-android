package com.cloudrail.fileviewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.cloudrail.si.types.CloudMetaData;
import com.cloudrail.si.interfaces.CloudStorage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 * to handle interaction events.
 * Use the {@link Files#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Files extends Fragment {
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_SERVICE = "service";
    private static final int FILE_SELECT = 0;
    private final static String[] DISPLAYABLES = new String[] {"jpg", "png", "jpeg", "pdf", "mp3", "txt", "gif", "wav", "mpeg", "mp4"};

    private int currentService;
    private ListView list = null;
    private String currentPath;
    private View selectedItem;
    private ProgressBar spinner;

    private Context context;
    private Activity activity = null;

    public Files() {
        // Required empty public constructor
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT: {
                if(resultCode == getOwnActivity().RESULT_OK) {
                    final Uri uri = data.getData();
                    final String name;
                    String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
                    Cursor metaCursor = getOwnActivity().getContentResolver().query(uri, projection, null, null, null);

                    if(metaCursor == null) {
                        throw new RuntimeException("Could not read file name.");
                    }

                    try {
                        metaCursor.moveToFirst();
                        name = metaCursor.getString(0);
                    } finally {
                        metaCursor.close();
                    }

                    this.uploadItem(name, uri);
                }
                break;
            }
            default: super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment Files.
     */
    public static Files newInstance(int service) {
        Files fragment = new Files();
        Bundle args = new Bundle();
        args.putInt(ARG_SERVICE, service);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentService = getArguments().getInt(ARG_SERVICE);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_files, container, false);
        TextView tv = (TextView) v.findViewById(R.id.service_name);

        switch (this.currentService) {
            case 1: {
                tv.setText("My Dropbox:");
                break;
            }
            case 2: {
                tv.setText("My Box:");
                break;
            }
            case 3: {
                tv.setText("My GoogleDrive:");
                break;
            }
            case 4: {
                tv.setText("My OneDrive:");
                break;
            }
        }

        this.list = (ListView) v.findViewById(R.id.listView);

        this.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startSpinner();
                LinearLayout ll = (LinearLayout) view;
                TextView tv = (TextView) ll.findViewById(R.id.list_item);
                final String name = (String) tv.getText();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String next = currentPath;
                        if (!currentPath.equals("/")) {
                            next += "/";
                        }
                        next += name;

                        CloudMetaData info = getService().getMetadata(next);
                        if (info.getFolder()) {
                            setNewPath(next);
                        } else {
                            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            InputStream data = getService().download(next);
                            File file = new File(path, name);

                            try {
                                FileOutputStream stream = new FileOutputStream(file, true);
                                pipe(data, stream);
                                data.close();
                                stream.close();
                            } catch (Exception e) {
                                stopSpinner();
                                e.printStackTrace();
                            }

                            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            intent.setData(Uri.fromFile(file));
                            getOwnActivity().sendBroadcast(intent);

                            String ext = getMimeType(name);
                            String mime = null;
                            stopSpinner();
                            if (ext != null && isDisplayable(ext)) {
                                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                                intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(Uri.fromFile(file), mime);
                                startActivity(intent);
                            } else {
                                getOwnActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(context, "Download complete! Stored to download folder.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }
                }).start();
            }
        });

        this.list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                selectedItem = view;
                PopupMenu popupMenu = new PopupMenu(getOwnActivity(), view);
                MenuInflater menuInflater = getOwnActivity().getMenuInflater();
                menuInflater.inflate(R.menu.selected_item_bar, popupMenu.getMenu());

                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_delete: {
                                removeItem();
                                return true;
                            }
                            default:
                                return false;
                        }
                    }
                });

                popupMenu.show();

                return true;
            }
        });

        this.spinner = (ProgressBar) v.findViewById(R.id.spinner);
        this.spinner.setVisibility(View.GONE);
        this.currentPath = "/";
        this.updateList();

        return v;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onAttach(Activity context) {
        super.onAttach(context);
        this.context = context;
        this.activity = context;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.action_bar, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh: {
                this.updateList();
                break;
            }
            case R.id.action_upload: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                try {
                    this.startActivityForResult(Intent.createChooser(intent, "Select a File to Upload"), FILE_SELECT);
                } catch(android.content.ActivityNotFoundException e) {
                    Toast.makeText(context, "Please install a file manager to perform this action!", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
        return true;
    }

    public boolean onBackPressed() {
        if(this.currentPath.equals("/")) {
            return true;
        } else {
            int pos = this.currentPath.lastIndexOf("/");
            String newPath = "/";
            if(pos != 0) {
                newPath = this.currentPath.substring(0, pos);
            }
            this.setNewPath(newPath);
        }
        return false;
    }

    private CloudStorage getService() {
        return Services.getInstance().getService(this.currentService);
    }

    private void updateList() {
        this.startSpinner();
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<CloudMetaData> items = getService().getChildren(currentPath);
                final List<CloudMetaData> files = sortList(items);

                getOwnActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        CloudMetadataAdapter listAdapter = new CloudMetadataAdapter(context, R.layout.list_item, files);
                        list.setAdapter(listAdapter);
                        stopSpinner();
                    }
                });
            }
        }).start();
    }

    private void setNewPath(String path) {
        this.currentPath = path;
        this.updateList();
    }

    private void pipe(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int size = is.read(buffer);
        while(size > -1) {
            os.write(buffer, 0, size);
            size = is.read(buffer);
        }
    }

    private boolean isDisplayable(String ext) {
        for(String a : DISPLAYABLES) {
            if(a.equals(ext)) return true;
        }
        return false;
    }

    private String getMimeType(String name) {
        int pos = name.lastIndexOf(".");

        if(pos == -1) return null;

        return name.substring(pos + 1);
    }

    private List<CloudMetaData> sortList(List<CloudMetaData> list) {
        List<CloudMetaData> folders = new ArrayList<>();
        List<CloudMetaData> files = new ArrayList<>();

        for(CloudMetaData cmd : list) {
            if(cmd == null) continue;

            if(cmd.getFolder()) {
                folders.add(cmd);
            } else {
                files.add(cmd);
            }
        }

        folders.addAll(files);
        return folders;
    }

    private void removeItem() {
        this.startSpinner();
        TextView tv = (TextView) this.selectedItem.findViewById(R.id.list_item);
        final String name = (String) tv.getText();
        CloudMetaData cloudMetaData = new CloudMetaData();
        cloudMetaData.setName(name);
        ArrayAdapter<CloudMetaData> adapter = (ArrayAdapter<CloudMetaData>) this.list.getAdapter();
        adapter.remove(cloudMetaData);

        new Thread(new Runnable() {
            @Override
            public void run() {
                String next = currentPath;
                if(!currentPath.equals("/")) {
                    next += "/";
                }
                next += name;
                getService().delete(next);
                updateList();
            }
        }).start();
    }

    private void uploadItem(final String name, final Uri uri) {
        startSpinner();
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream fs = null;
                long size = -1;
                try {
                    fs = getOwnActivity().getContentResolver().openInputStream(uri);
                    size = getOwnActivity().getContentResolver().openAssetFileDescriptor(uri, "r").getLength();
                } catch (Exception e) {
                    stopSpinner();
                    getOwnActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Unable to access file!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                String next = currentPath;
                if(!currentPath.equals("/")) {
                    next += "/";
                }
                next += name;
                getService().upload(next, fs, size, true);

                getOwnActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateList();
                    }
                });
            }
        }).start();
    }

    private void startSpinner() {
        getOwnActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spinner.setVisibility(View.VISIBLE);
            }
        });
    }

    private void stopSpinner() {
        getOwnActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spinner.setVisibility(View.GONE);
            }
        });
    }

    private Activity getOwnActivity() {
        if(this.activity == null) {
            return this.getActivity();
        } else {
            return this.activity;
        }
    }
}
