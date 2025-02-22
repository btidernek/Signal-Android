/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.btider.dediapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import org.btider.dediapp.attachments.DatabaseAttachment;
import org.btider.dediapp.crypto.IdentityKeyUtil;
import org.btider.dediapp.crypto.MasterSecret;
import org.btider.dediapp.database.AttachmentDatabase;
import org.btider.dediapp.database.DatabaseFactory;
import org.btider.dediapp.database.MmsDatabase;
import org.btider.dediapp.database.PushDatabase;
import org.btider.dediapp.database.model.MessageRecord;
import org.btider.dediapp.jobs.AttachmentDownloadJob;
import org.btider.dediapp.jobs.CreateSignedPreKeyJob;
import org.btider.dediapp.jobs.DirectoryRefreshJob;
import org.btider.dediapp.jobs.PushDecryptJob;
import org.btider.dediapp.jobs.RefreshAttributesJob;
import org.btider.dediapp.notifications.MessageNotifier;
import org.btider.dediapp.service.KeyCachingService;
import org.btider.dediapp.util.FileUtils;
import org.btider.dediapp.util.TextSecurePreferences;
import org.btider.dediapp.util.Util;
import org.btider.dediapp.util.VersionTracker;
import org.btider.dediapp.R;
import org.btider.dediapp.attachments.DatabaseAttachment;
import org.btider.dediapp.crypto.IdentityKeyUtil;
import org.btider.dediapp.crypto.MasterSecret;
import org.btider.dediapp.database.AttachmentDatabase;
import org.btider.dediapp.database.DatabaseFactory;
import org.btider.dediapp.database.MmsDatabase;
import org.btider.dediapp.database.MmsDatabase.Reader;
import org.btider.dediapp.database.PushDatabase;
import org.btider.dediapp.database.model.MessageRecord;
import org.btider.dediapp.jobs.AttachmentDownloadJob;
import org.btider.dediapp.jobs.CreateSignedPreKeyJob;
import org.btider.dediapp.jobs.DirectoryRefreshJob;
import org.btider.dediapp.jobs.PushDecryptJob;
import org.btider.dediapp.jobs.RefreshAttributesJob;
import org.btider.dediapp.notifications.MessageNotifier;
import org.btider.dediapp.service.KeyCachingService;
import org.btider.dediapp.util.FileUtils;
import org.btider.dediapp.util.TextSecurePreferences;
import org.btider.dediapp.util.Util;
import org.btider.dediapp.util.VersionTracker;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class DatabaseUpgradeActivity extends BaseActivity {
  private static final String TAG = DatabaseUpgradeActivity.class.getSimpleName();

  public static final int NO_MORE_KEY_EXCHANGE_PREFIX_VERSION  = 46;
  public static final int MMS_BODY_VERSION                     = 46;
  public static final int TOFU_IDENTITIES_VERSION              = 50;
  public static final int CURVE25519_VERSION                   = 63;
  public static final int ASYMMETRIC_MASTER_SECRET_FIX_VERSION = 73;
  public static final int NO_V1_VERSION                        = 83;
  public static final int SIGNED_PREKEY_VERSION                = 83;
  public static final int NO_DECRYPT_QUEUE_VERSION             = 113;
  public static final int PUSH_DECRYPT_SERIAL_ID_VERSION       = 131;
  public static final int MIGRATE_SESSION_PLAINTEXT            = 136;
  public static final int CONTACTS_ACCOUNT_VERSION             = 136;
  public static final int MEDIA_DOWNLOAD_CONTROLS_VERSION      = 151;
  public static final int REDPHONE_SUPPORT_VERSION             = 157;
  public static final int NO_MORE_CANONICAL_DB_VERSION         = 276;
  public static final int PROFILES                             = 289;
  public static final int SCREENSHOTS                          = 300;
  public static final int PERSISTENT_BLOBS                     = 317;
  public static final int INTERNALIZE_CONTACTS                 = 317;
  public static final int SQLCIPHER                            = 334;
  public static final int SQLCIPHER_COMPLETE                   = 352;
  public static final int REMOVE_JOURNAL                       = 353;
  public static final int REMOVE_CACHE                         = 354;
  public static final int FULL_TEXT_SEARCH                     = 358;

  private static final SortedSet<Integer> UPGRADE_VERSIONS = new TreeSet<Integer>() {{
    add(NO_MORE_KEY_EXCHANGE_PREFIX_VERSION);
    add(TOFU_IDENTITIES_VERSION);
    add(CURVE25519_VERSION);
    add(ASYMMETRIC_MASTER_SECRET_FIX_VERSION);
    add(NO_V1_VERSION);
    add(SIGNED_PREKEY_VERSION);
    add(NO_DECRYPT_QUEUE_VERSION);
    add(PUSH_DECRYPT_SERIAL_ID_VERSION);
    add(MIGRATE_SESSION_PLAINTEXT);
    add(MEDIA_DOWNLOAD_CONTROLS_VERSION);
    add(REDPHONE_SUPPORT_VERSION);
    add(NO_MORE_CANONICAL_DB_VERSION);
    add(SCREENSHOTS);
    add(INTERNALIZE_CONTACTS);
    add(PERSISTENT_BLOBS);
    add(SQLCIPHER);
    add(SQLCIPHER_COMPLETE);
    add(REMOVE_CACHE);
    add(FULL_TEXT_SEARCH);
  }};

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.masterSecret = KeyCachingService.getMasterSecret(this);

    if (needsUpgradeTask()) {
      Log.w("DatabaseUpgradeActivity", "Upgrading...");
      setContentView(R.layout.database_upgrade_activity);

      ProgressBar indeterminateProgress = findViewById(R.id.indeterminate_progress);
      ProgressBar determinateProgress   = findViewById(R.id.determinate_progress);

      new DatabaseUpgradeTask(indeterminateProgress, determinateProgress)
          .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, VersionTracker.getLastSeenVersion(this));
    } else {
      VersionTracker.updateLastSeenVersion(this);
      updateNotifications(this);
      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }
  }

  private boolean needsUpgradeTask() {
    int currentVersionCode = Util.getCurrentApkReleaseVersion(this);
    int lastSeenVersion    = VersionTracker.getLastSeenVersion(this);

    Log.w("DatabaseUpgradeActivity", "LastSeenVersion: " + lastSeenVersion);

    if (lastSeenVersion >= currentVersionCode)
      return false;

    for (int version : UPGRADE_VERSIONS) {
      Log.w("DatabaseUpgradeActivity", "Comparing: " + version);
      if (lastSeenVersion < version)
        return true;
    }

    return false;
  }

  public static boolean isUpdate(Context context) {
    try {
      int currentVersionCode  = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
      int previousVersionCode = VersionTracker.getLastSeenVersion(context);

      return previousVersionCode < currentVersionCode;
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void updateNotifications(final Context context) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.updateNotification(context);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  public interface DatabaseUpgradeListener {
    public void setProgress(int progress, int total);
  }

  @SuppressLint("StaticFieldLeak")
  private class DatabaseUpgradeTask extends AsyncTask<Integer, Double, Void>
      implements DatabaseUpgradeListener
  {

    private final ProgressBar indeterminateProgress;
    private final ProgressBar determinateProgress;

    DatabaseUpgradeTask(ProgressBar indeterminateProgress, ProgressBar determinateProgress) {
      this.indeterminateProgress = indeterminateProgress;
      this.determinateProgress   = determinateProgress;
    }

    @Override
    protected Void doInBackground(Integer... params) {
      Context context = DatabaseUpgradeActivity.this.getApplicationContext();

      Log.w("DatabaseUpgradeActivity", "Running background upgrade..");
      DatabaseFactory.getInstance(DatabaseUpgradeActivity.this)
                     .onApplicationLevelUpgrade(context, masterSecret, params[0], this);

      if (params[0] < CURVE25519_VERSION) {
        IdentityKeyUtil.migrateIdentityKeys(context, masterSecret);
      }

      if (params[0] < NO_V1_VERSION) {
        File v1sessions = new File(context.getFilesDir(), "sessions");

        if (v1sessions.exists() && v1sessions.isDirectory()) {
          File[] contents = v1sessions.listFiles();

          if (contents != null) {
            for (File session : contents) {
              session.delete();
            }
          }

          v1sessions.delete();
        }
      }

      if (params[0] < SIGNED_PREKEY_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new CreateSignedPreKeyJob(context));
      }

      if (params[0] < NO_DECRYPT_QUEUE_VERSION) {
        scheduleMessagesInPushDatabase(context);
      }

      if (params[0] < PUSH_DECRYPT_SERIAL_ID_VERSION) {
        scheduleMessagesInPushDatabase(context);
      }

      if (params[0] < MIGRATE_SESSION_PLAINTEXT) {
//        new TextSecureSessionStore(context, masterSecret).migrateSessions();
//        new TextSecurePreKeyStore(context, masterSecret).migrateRecords();

        IdentityKeyUtil.migrateIdentityKeys(context, masterSecret);
        scheduleMessagesInPushDatabase(context);;
      }

      if (params[0] < CONTACTS_ACCOUNT_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new DirectoryRefreshJob(getApplicationContext(), false));
      }

      if (params[0] < MEDIA_DOWNLOAD_CONTROLS_VERSION) {
        schedulePendingIncomingParts(context);
      }

      if (params[0] < REDPHONE_SUPPORT_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new RefreshAttributesJob(getApplicationContext()));
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new DirectoryRefreshJob(getApplicationContext(), false));
      }

      if (params[0] < PROFILES) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new DirectoryRefreshJob(getApplicationContext(), false));
      }

      if (params[0] < SCREENSHOTS) {
        boolean screenSecurity = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(TextSecurePreferences.SCREEN_SECURITY_PREF, true);
        TextSecurePreferences.setScreenSecurityEnabled(getApplicationContext(), screenSecurity);
      }

      if (params[0] < PERSISTENT_BLOBS) {
        File externalDir = context.getExternalFilesDir(null);

        if (externalDir != null && externalDir.isDirectory() && externalDir.exists()) {
          for (File blob : externalDir.listFiles()) {
            if (blob.exists() && blob.isFile()) blob.delete();
          }
        }
      }

      if (params[0] < INTERNALIZE_CONTACTS) {
        if (TextSecurePreferences.isPushRegistered(getApplicationContext())) {
          TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(getApplicationContext(), true);
        }
      }

      if (params[0] < SQLCIPHER) {
        scheduleMessagesInPushDatabase(context);
      }

      if (params[0] < SQLCIPHER_COMPLETE) {
        File file = context.getDatabasePath("messages.db");
        if (file != null && file.exists()) file.delete();
      }

      if (params[0] < REMOVE_JOURNAL) {
        File file = context.getDatabasePath("messages.db-journal");
        if (file != null && file.exists()) file.delete();
      }

      if (params[0] < REMOVE_CACHE) {
        try {
          FileUtils.deleteDirectoryContents(context.getCacheDir());
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      return null;
    }

    private void schedulePendingIncomingParts(Context context) {
      final AttachmentDatabase attachmentDb       = DatabaseFactory.getAttachmentDatabase(context);
      final MmsDatabase mmsDb              = DatabaseFactory.getMmsDatabase(context);
      final List<DatabaseAttachment> pendingAttachments = DatabaseFactory.getAttachmentDatabase(context).getPendingAttachments();

      Log.w(TAG, pendingAttachments.size() + " pending parts.");
      for (DatabaseAttachment attachment : pendingAttachments) {
        final MmsDatabase.Reader reader = mmsDb.readerFor(mmsDb.getMessage(attachment.getMmsId()));
        final MessageRecord record = reader.getNext();

        if (attachment.hasData()) {
          Log.w(TAG, "corrected a pending media part " + attachment.getAttachmentId() + "that already had data.");
          attachmentDb.setTransferState(attachment.getMmsId(), attachment.getAttachmentId(), AttachmentDatabase.TRANSFER_PROGRESS_DONE);
        } else if (record != null && !record.isOutgoing() && record.isPush()) {
          Log.w(TAG, "queuing new attachment download job for incoming push part " + attachment.getAttachmentId() + ".");
          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new AttachmentDownloadJob(context, attachment.getMmsId(), attachment.getAttachmentId(), false,attachment.getSize()));
        }
        reader.close();
      }
    }

    private void scheduleMessagesInPushDatabase(Context context) {
      PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(context);
      Cursor       pushReader   = null;

      try {
        pushReader = pushDatabase.getPending();

        while (pushReader != null && pushReader.moveToNext()) {
          ApplicationContext.getInstance(getApplicationContext())
                            .getJobManager()
                            .add(new PushDecryptJob(getApplicationContext(),
                                                    pushReader.getLong(pushReader.getColumnIndexOrThrow(PushDatabase.ID))));
        }
      } finally {
        if (pushReader != null)
          pushReader.close();
      }
    }

    @Override
    protected void onProgressUpdate(Double... update) {
      indeterminateProgress.setVisibility(View.GONE);
      determinateProgress.setVisibility(View.VISIBLE);

      double scaler = update[0];
      determinateProgress.setProgress((int)Math.floor(determinateProgress.getMax() * scaler));
    }

    @Override
    protected void onPostExecute(Void result) {
      VersionTracker.updateLastSeenVersion(DatabaseUpgradeActivity.this);
      updateNotifications(DatabaseUpgradeActivity.this);

      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }

    @Override
    public void setProgress(int progress, int total) {
      publishProgress(((double)progress / (double)total));
    }
  }

}
