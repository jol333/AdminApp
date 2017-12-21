package com.dbapp.ashworth.adminapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;

import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.Metadata;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for file list
 */
public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.MetadataViewHolder> {
    private final Picasso mPicasso;
    private final Callback mCallback;
    private final Context fContext;
    private List<Metadata> mFiles;

    public FilesAdapter(Context c, Picasso picasso, Callback callback) {
        mPicasso = picasso;
        mCallback = callback;
        fContext = c;
    }

    public void setFiles(List<Metadata> files) {
        mFiles = Collections.unmodifiableList(new ArrayList<>(files));
        notifyDataSetChanged();
    }

    @Override
    public MetadataViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        Context context = viewGroup.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.files_item, viewGroup, false);
        return new MetadataViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MetadataViewHolder metadataViewHolder, int i) {
        metadataViewHolder.bind(mFiles.get(i));
    }

    @Override
    public long getItemId(int position) {
        return mFiles.get(position).getPathLower().hashCode();
    }

    @Override
    public int getItemCount() {
        return mFiles == null ? 0 : mFiles.size();
    }

    public interface Callback {
        void onFolderClicked(FolderMetadata folder);

        void onFileClicked(FileMetadata file);
    }

    public class MetadataViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final TextView mTextView;
        private final ImageView mImageView;
        private final ImageView unShare;
        private Metadata mItem;

        public MetadataViewHolder(View itemView) {
            super(itemView);
            mImageView = (ImageView) itemView.findViewById(R.id.image);
            mTextView = (TextView) itemView.findViewById(R.id.text);
            unShare = (ImageView) itemView.findViewById(R.id.unshare);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {

            if (mItem instanceof FolderMetadata) {
                mCallback.onFolderClicked((FolderMetadata) mItem);
            } else if (mItem instanceof FileMetadata) {
                mCallback.onFileClicked((FileMetadata) mItem);
            }
        }

        public void bind(final Metadata item) {
            mItem = item;
            mTextView.setText(mItem.getName());

            // Load based on file path
            // Prepending a magic scheme to get it to
            // be picked up by DropboxPicassoRequestHandler

            if (item instanceof FileMetadata) {
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                String ext = item.getName().substring(item.getName().indexOf(".") + 1);
                String type = mime.getMimeTypeFromExtension(ext);
                if (type != null && type.startsWith("image/")) {
                    mPicasso.load(FileThumbnailRequestHandler.buildPicassoUri((FileMetadata) item))
                            .placeholder(R.drawable.ic_photo_grey_600_36dp)
                            .error(R.drawable.ic_photo_grey_600_36dp)
                            .into(mImageView);
                } else {
                    mPicasso.load(R.drawable.ic_insert_drive_file_blue_36dp)
                            .noFade()
                            .into(mImageView);
                }
            } else if (item instanceof FolderMetadata) {
                mPicasso.load(R.drawable.ic_folder_blue_36dp)
                        .noFade()
                        .into(mImageView);

                String folderPath = item.getPathLower();

                if (folderPath.contains("(unshared)") || folderPath.substring(8).contains("/")) {
                    unShare.setVisibility(View.GONE);
                } else {
                    unShare.setVisibility(View.VISIBLE);
                    mPicasso.load(R.drawable.ic_unshare)
                            .noFade()
                            .into(unShare);

                    unShare.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(fContext);
                            builder
                                    .setTitle("Remove clerk")
                                    .setMessage("Do you really want to remove this clerk?\nThis folder will be unshared and clerk won't be able to add files anymore.")
                                    .setIcon(R.drawable.ic_alert_blue)
                                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            unshareFolder((FolderMetadata) item); // Function to unshare folder
                                        }
                                    })
                                    .setNegativeButton("No", null)                        //Do nothing on no
                                    .show();
                        }
                    });
                }
            }
        }

        //Function to unshare folder
        private void unshareFolder(final FolderMetadata item) {
            final ProgressDialog progressDialog = new ProgressDialog(fContext);

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    progressDialog.setCancelable(false);
                    progressDialog.setMessage("Unsharing the folder...");
                    progressDialog.show();
                    unShare.setVisibility(View.GONE);
                    mTextView.setText(item.getName() + " (...)");
                }

                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        if (item.getSharedFolderId() != null)
                            DropboxClientFactory.getClient().sharing().unshareFolder(item.getSharedFolderId());
                        DropboxClientFactory.getClient().files().move(item.getPathDisplay(), item.getPathDisplay().concat(" (unshared)"));
                    } catch (Exception e) {
                        Log.e("Folder unshare error", e.getMessage(), e);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    progressDialog.dismiss();
//                    mTextView.setText(item.getName() + " (unshared)");
                    ((FilesActivity) fContext).loadData();
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
        }
    }
}
