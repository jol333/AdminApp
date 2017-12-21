package com.dbapp.ashworth.adminapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.sharing.AccessLevel;
import com.dropbox.core.v2.sharing.AddMember;
import com.dropbox.core.v2.sharing.MemberSelector;
import com.dropbox.core.v2.sharing.ShareFolderLaunch;
import com.dropbox.core.v2.sharing.SharedFolderMetadata;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import static com.dbapp.ashworth.adminapp.DropboxClientFactory.getClient;


/**
 * Activity that displays the content of a path in dropbox and lets users navigate folders,
 * and upload/download files
 */
public class FilesActivity extends DropboxActivity {
    public final static String EXTRA_PATH = "FilesActivity_Path";
    private static final String TAG = FilesActivity.class.getName();
    private static final int PICKFILE_REQUEST_CODE = 1;

    private String mPath;
    private FilesAdapter mFilesAdapter;
    private FileMetadata mSelectedFile;

    public static Intent getIntent(Context context, String path) {
        Intent filesIntent = new Intent(context, FilesActivity.class);
        filesIntent.putExtra(FilesActivity.EXTRA_PATH, path);
        return filesIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        mPath = path == null ? "" : path;

        setContentView(R.layout.activity_files);

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(mPath);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        Button b = (Button) findViewById(R.id.add_new_clerk);
        if (mPath.equals("/clerks")) {

        } else if (mPath.substring(8).contains("/") || mPath.contains(" (unshared)")) {
            b.setVisibility(View.GONE);
        } else {
            b.setText("Remove Clerk (Unshare)");
            b.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_unshare_white, 0, 0, 0);
        }
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //performWithPermissions(FileAction.UPLOAD);
                if (mPath.equals("/clerks")) {
                    showAddClerkDialog(); //Function to add new clerk
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(FilesActivity.this);
                    builder
                            .setTitle("Remove clerk")
                            .setMessage("Do you really want to remove this clerk?\nThis folder will be unshared and clerk won't be able to add files anymore.")
                            .setIcon(R.drawable.ic_alert_blue)
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    unshareFolder(); // Function to unshare folder
                                }
                            })
                            .setNegativeButton("No", null)                        //Do nothing on no
                            .show();
                }
            }
        });

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.files_list);
        mFilesAdapter = new FilesAdapter(this, PicassoClient.getPicasso(), new FilesAdapter.Callback() {

            @Override
            public void onFolderClicked(FolderMetadata folder) {
                startActivity(FilesActivity.getIntent(FilesActivity.this, folder.getPathLower()));
            }

            @Override
            public void onFileClicked(final FileMetadata file) {
                mSelectedFile = file;
                performWithPermissions(com.dbapp.ashworth.adminapp.FilesActivity.FileAction.DOWNLOAD);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mFilesAdapter);

        mSelectedFile = null;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        finish();
        return true;
    }

//    private SharedFolderMetadata isShared() {
//        final SharedFolderMetadata sfm;
//        new AsyncTask<Void, Integer, SharedFolderMetadata>() {
//            @Override
//            protected SharedFolderMetadata doInBackground(Void... params) {
//                try {
//                    DbxClientV2 dbxClient = DropboxClientFactory.getClient();
//                    List<SharedFolderMetadata> myList = dbxClient.sharing().listFolders().getEntries();
//
//                    for (SharedFolderMetadata folder : myList) {
//                        //Log.e("Folder name", folder.getName());
//                        if (folder.getPathLower() != null && folder.getSharedFolderId() != null) {
//                            return folder;
//                        }
//                    }
//
//                } catch (Exception e) {
//                    Log.e("Folder unshare error", e.getMessage(), e);
//                }
//                return null;
//            }
//
//            @Override
//            protected void onPostExecute(SharedFolderMetadata sharedFolderMetadata) {
//                super.onPostExecute(sharedFolderMetadata);
//                sfm = sharedFolderMetadata;
//            }
//        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
//    }

    private void unshareFolder() {
        final ProgressDialog progressDialog = new ProgressDialog(FilesActivity.this);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.setCancelable(false);
                progressDialog.setMessage("Unsharing the folder...");
                progressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    DbxClientV2 dbxClient = DropboxClientFactory.getClient();
                    List<SharedFolderMetadata> myList = dbxClient.sharing().listFolders().getEntries();
                    Boolean sharedFlag = true;
                    for (SharedFolderMetadata folder : myList) {
                        //Log.e("Folder name", folder.getName());
                        if (folder.getPathLower() != null) {
                            if (folder.getPathLower().equals(mPath)) {
                                if (folder.getSharedFolderId() != null) {
                                    sharedFlag = false;
                                    dbxClient.sharing().unshareFolder(folder.getSharedFolderId());
                                    //Wait for 2.5 seconds to avoid too_many_write_operations error
                                    Thread.sleep(2500);
                                }
                                dbxClient.files().move("/Clerks/" + folder.getName(), "/Clerks/" + folder.getName() + " (unshared)");
                                break;
                            }
                        }
                    }
                    if (sharedFlag) dbxClient.files().move(mPath, mPath + " (unshared)");
                } catch (Exception e) {
                    Log.e("Folder unshare error", e.getMessage(), e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                progressDialog.dismiss();
                finish();
                startActivity(FilesActivity.getIntent(FilesActivity.this, mPath + " (unshared)"));
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
    }

    private void showAddClerkDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(FilesActivity.this)
                .setView(R.layout.dialog_new_clerk)
                .create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.show();
        Button addClerkBtn = (Button) dialog.findViewById(R.id.add_clerk_btn);
        addClerkBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String clerkName = ((EditText) dialog.findViewById(R.id.clerk_name)).getText().toString();
                final String clerkDbId = ((EditText) dialog.findViewById(R.id.dropbox_mail)).getText().toString();

                if (!clerkName.isEmpty() && !clerkDbId.isEmpty()) {
                    dialog.dismiss();
                    final ProgressDialog progressDialog = new ProgressDialog(FilesActivity.this);
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected void onPreExecute() {
                            super.onPreExecute();
                            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                            progressDialog.setCancelable(false);
                            progressDialog.setMessage("Adding new clerk...");
                            progressDialog.show();
                        }

                        @Override
                        protected Void doInBackground(Void... params) {
                            try {
                                DbxClientV2 dbxClient = DropboxClientFactory.getClient();
                                //dbxClient.files().createFolder("/" + clerkName);

                                List<AddMember> list = new ArrayList<>();
                                AddMember newMember = new AddMember(MemberSelector.email(clerkDbId), AccessLevel.EDITOR);
                                list.add(newMember);
                                ShareFolderLaunch sfl = dbxClient.sharing().shareFolder("/Clerks/" + clerkName);
                                dbxClient.sharing().addFolderMember(sfl.getCompleteValue().getSharedFolderId(), list);

                            } catch (Exception e) {
                                progressDialog.dismiss();
                                Log.e("Folder creation error", e.getMessage(), e);
                                //Toast.makeText(FilesActivity.this, "Enter a valid name and Dropbox id", Toast.LENGTH_LONG).show();
                            }
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            super.onPostExecute(aVoid);
                            progressDialog.dismiss();
                            loadData();
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
                } else
                    Toast.makeText(FilesActivity.this, "Enter a valid name and Dropbox id", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void launchFilePicker() {
        // Launch intent to pick file for upload
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICKFILE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICKFILE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {

                // This is the result of a call to launchFilePicker
                uploadFile(data.getData().toString());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int actionCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        FileAction action = FileAction.fromCode(actionCode);

        boolean granted = true;
        for (int i = 0; i < grantResults.length; ++i) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Log.w(TAG, "User denied " + permissions[i] +
                        " permission to perform file action: " + action);
                granted = false;
                break;
            }
        }

        if (granted) {
            performAction(action);
        } else {
            switch (action) {
                case UPLOAD:
                    Toast.makeText(this,
                            "Can't upload file: read access denied. " +
                                    "Please grant storage permissions to use this functionality.",
                            Toast.LENGTH_LONG)
                            .show();
                    break;
                case DOWNLOAD:
                    Toast.makeText(this,
                            "Can't download file: write access denied. " +
                                    "Please grant storage permissions to use this functionality.",
                            Toast.LENGTH_LONG)
                            .show();
                    break;
            }
        }
    }

    private void performAction(FileAction action) {
        switch (action) {
            case UPLOAD:
                launchFilePicker();
                break;
            case DOWNLOAD:
                if (mSelectedFile != null) {
                    downloadFile(mSelectedFile);
                } else {
                    Log.e(TAG, "No file selected to download.");
                }
                break;
            default:
                Log.e(TAG, "Can't perform unhandled file action: " + action);
        }
    }

    // Function that reload files on screen
    @Override
    protected void loadData() {

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.setMessage("Loading files and folders...");
        dialog.show();

        new ListFolderTask(getClient(), new ListFolderTask.Callback() {
            @Override
            public void onDataLoaded(ListFolderResult result) {
                dialog.dismiss();

                mFilesAdapter.setFiles(result.getEntries());
            }

            @Override
            public void onError(Exception e) {
                dialog.dismiss();

                Log.e(TAG, "Failed to list folder.", e);
                Toast.makeText(FilesActivity.this,
                        "Failed to list files and folders.",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }).execute(mPath);
    }

    private void downloadFile(FileMetadata file) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.setMessage("Downloading");
        dialog.show();

        new DownloadFileTask(FilesActivity.this, getClient(), new DownloadFileTask.Callback() {
            @Override
            public void onDownloadComplete(File result) {
                dialog.dismiss();

                if (result != null) {
                    viewFileInExternalApp(result);
                }
            }

            @Override
            public void onError(Exception e) {
                dialog.dismiss();

                Log.e(TAG, "Failed to download file.", e);
                Toast.makeText(FilesActivity.this,
                        "An error has occurred",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }).execute(file);

    }

    private void viewFileInExternalApp(File result) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        String ext = result.getName().substring(result.getName().indexOf(".") + 1);
        String type = mime.getMimeTypeFromExtension(ext);

        intent.setDataAndType(Uri.fromFile(result), type);

        // Check for a handler first to avoid a crash
        PackageManager manager = getPackageManager();
        List<ResolveInfo> resolveInfo = manager.queryIntentActivities(intent, 0);
        if (resolveInfo.size() > 0) {
            startActivity(intent);
        }
    }

    private void uploadFile(String fileUri) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.setMessage("Uploading");
        dialog.show();

        new UploadFileTask(this, getClient(), new UploadFileTask.Callback() {
            @Override
            public void onUploadComplete(FileMetadata result) {
                dialog.dismiss();

                String message = result.getName() + " size " + result.getSize() + " modified " +
                        DateFormat.getDateTimeInstance().format(result.getClientModified());
                Toast.makeText(FilesActivity.this, message, Toast.LENGTH_LONG)
                        .show();

                // Reload the folder
                loadData();
            }

            @Override
            public void onError(Exception e) {
                dialog.dismiss();

                Log.e(TAG, "Failed to upload file.", e);
                Toast.makeText(FilesActivity.this,
                        "An error has occurred",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }).execute(fileUri, mPath);
    }

    private void performWithPermissions(final FileAction action) {
        if (hasPermissionsForAction(action)) {
            performAction(action);
            return;
        }

        if (shouldDisplayRationaleForAction(action)) {
            new AlertDialog.Builder(this)
                    .setMessage("This app requires storage access to download and upload files.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissionsForAction(action);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        } else {
            requestPermissionsForAction(action);
        }
    }

    private boolean hasPermissionsForAction(FileAction action) {
        for (String permission : action.getPermissions()) {
            int result = ContextCompat.checkSelfPermission(this, permission);
            if (result == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldDisplayRationaleForAction(FileAction action) {
        for (String permission : action.getPermissions()) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    private void requestPermissionsForAction(FileAction action) {
        ActivityCompat.requestPermissions(
                this,
                action.getPermissions(),
                action.getCode()
        );
    }

    private enum FileAction {
        DOWNLOAD(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        UPLOAD(Manifest.permission.READ_EXTERNAL_STORAGE);

        private static final FileAction[] values = values();

        private final String[] permissions;

        FileAction(String... permissions) {
            this.permissions = permissions;
        }

        public static FileAction fromCode(int code) {
            if (code < 0 || code >= values.length) {
                throw new IllegalArgumentException("Invalid FileAction code: " + code);
            }
            return values[code];
        }

        public int getCode() {
            return ordinal();
        }

        public String[] getPermissions() {
            return permissions;
        }
    }
}
