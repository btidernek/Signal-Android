package org.btider.dediapp.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.btider.dediapp.attachments.Attachment;
import org.btider.dediapp.attachments.AttachmentId;
import org.btider.dediapp.database.DatabaseFactory;
import org.btider.dediapp.providers.PersistentBlobProvider;
import org.btider.dediapp.providers.SingleUseBlobProvider;
import org.btider.dediapp.attachments.Attachment;
import org.btider.dediapp.attachments.AttachmentId;
import org.btider.dediapp.crypto.MasterSecret;
import org.btider.dediapp.database.DatabaseFactory;
import org.btider.dediapp.providers.PersistentBlobProvider;
import org.btider.dediapp.providers.PartProvider;
import org.btider.dediapp.providers.SingleUseBlobProvider;

import java.io.IOException;
import java.io.InputStream;

public class PartAuthority {

  private static final String PART_URI_STRING   = "content://org.btider.dediapp/part";
  private static final String THUMB_URI_STRING  = "content://org.btider.dediapp/thumb";
  private static final Uri    PART_CONTENT_URI  = Uri.parse(PART_URI_STRING);
  private static final Uri    THUMB_CONTENT_URI = Uri.parse(THUMB_URI_STRING);

  private static final int PART_ROW       = 1;
  private static final int THUMB_ROW      = 2;
  private static final int PERSISTENT_ROW = 3;
  private static final int SINGLE_USE_ROW = 4;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("org.btider.dediapp", "part/*/#", PART_ROW);
    uriMatcher.addURI("org.btider.dediapp", "thumb/*/#", THUMB_ROW);
    uriMatcher.addURI(PersistentBlobProvider.AUTHORITY, PersistentBlobProvider.EXPECTED_PATH_OLD, PERSISTENT_ROW);
    uriMatcher.addURI(PersistentBlobProvider.AUTHORITY, PersistentBlobProvider.EXPECTED_PATH_NEW, PERSISTENT_ROW);
    uriMatcher.addURI(SingleUseBlobProvider.AUTHORITY, SingleUseBlobProvider.PATH, SINGLE_USE_ROW);
  }

  public static InputStream getAttachmentStream(@NonNull Context context, @NonNull Uri uri)
      throws IOException
  {
    int match = uriMatcher.match(uri);
    try {
      switch (match) {
      case PART_ROW:       return DatabaseFactory.getAttachmentDatabase(context).getAttachmentStream(new PartUriParser(uri).getPartId(), 0);
      case THUMB_ROW:      return DatabaseFactory.getAttachmentDatabase(context).getThumbnailStream(new PartUriParser(uri).getPartId());
      case PERSISTENT_ROW: return PersistentBlobProvider.getInstance(context).getStream(context, ContentUris.parseId(uri));
      case SINGLE_USE_ROW: return SingleUseBlobProvider.getInstance().getStream(ContentUris.parseId(uri));
      default:             return context.getContentResolver().openInputStream(uri);
      }
    } catch (SecurityException se) {
      throw new IOException(se);
    }
  }

  public static @Nullable String getAttachmentFileName(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
    case THUMB_ROW:
    case PART_ROW:
      Attachment attachment = DatabaseFactory.getAttachmentDatabase(context).getAttachment(new PartUriParser(uri).getPartId());

      if (attachment != null) return attachment.getFileName();
      else                    return null;
    case PERSISTENT_ROW:
      return PersistentBlobProvider.getFileName(context, uri);
    case SINGLE_USE_ROW:
    default:
      return null;
    }
  }

  public static @Nullable Long getAttachmentSize(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
      case THUMB_ROW:
      case PART_ROW:
        Attachment attachment = DatabaseFactory.getAttachmentDatabase(context).getAttachment(new PartUriParser(uri).getPartId());

        if (attachment != null) return attachment.getSize();
        else                    return null;
      case PERSISTENT_ROW:
        return PersistentBlobProvider.getFileSize(context, uri);
      case SINGLE_USE_ROW:
      default:
        return null;
    }
  }

  public static @Nullable String getAttachmentContentType(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
      case THUMB_ROW:
      case PART_ROW:
        Attachment attachment = DatabaseFactory.getAttachmentDatabase(context).getAttachment(new PartUriParser(uri).getPartId());

        if (attachment != null) return attachment.getContentType();
        else                    return null;
      case PERSISTENT_ROW:
        return PersistentBlobProvider.getMimeType(context, uri);
      case SINGLE_USE_ROW:
      default:
        return null;
    }
  }

  public static Uri getAttachmentPublicUri(Uri uri) {
    PartUriParser partUri = new PartUriParser(uri);
    return PartProvider.getContentUri(partUri.getPartId());
  }

  public static Uri getAttachmentDataUri(AttachmentId attachmentId) {
    Uri uri = Uri.withAppendedPath(PART_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
    return ContentUris.withAppendedId(uri, attachmentId.getRowId());
  }

  public static Uri getAttachmentThumbnailUri(AttachmentId attachmentId) {
    Uri uri = Uri.withAppendedPath(THUMB_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
    return ContentUris.withAppendedId(uri, attachmentId.getRowId());
  }

  public static boolean isLocalUri(final @NonNull Uri uri) {
    int match = uriMatcher.match(uri);
    switch (match) {
    case PART_ROW:
    case THUMB_ROW:
    case PERSISTENT_ROW:
    case SINGLE_USE_ROW:
      return true;
    }
    return false;
  }
}
