/*
 * Copyright (C) 2012 Moxie Marlinspike
 * Copyright (C) 2013-2017 Open Whisper Systems
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
package org.btider.dediapp.database.model;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import org.btider.dediapp.recipients.Recipient;
import org.btider.dediapp.R;
import org.btider.dediapp.database.MmsSmsColumns;
import org.btider.dediapp.database.SmsDatabase;
import org.btider.dediapp.recipients.Recipient;
import org.btider.dediapp.util.ExpirationUtil;

/**
 * The message record model which represents thread heading messages.
 *
 * @author Moxie Marlinspike
 *
 */
public class ThreadRecord extends DisplayRecord {

  private @NonNull  final Context context;
  private @Nullable final Uri     snippetUri;
  private           final long    count;
  private           final int     unreadCount;
  private           final int     distributionType;
  private           final boolean archived;
  private           final long    expiresIn;
  private           final long    lastSeen;

  public ThreadRecord(@NonNull Context context, @NonNull String body, @Nullable Uri snippetUri,
                      @NonNull Recipient recipient, long date, long count, int unreadCount,
                      long threadId, int deliveryReceiptCount, int status, long snippetType,
                      int distributionType, boolean archived, long expiresIn, long lastSeen,
                      int readReceiptCount)
  {
    super(context, body, recipient, date, date, threadId, status, deliveryReceiptCount, snippetType, readReceiptCount);
    this.context          = context.getApplicationContext();
    this.snippetUri       = snippetUri;
    this.count            = count;
    this.unreadCount      = unreadCount;
    this.distributionType = distributionType;
    this.archived         = archived;
    this.expiresIn        = expiresIn;
    this.lastSeen         = lastSeen;
  }

  public @Nullable Uri getSnippetUri() {
    return snippetUri;
  }

  @Override
  public SpannableString getDisplayBody() {
    if (isGroupUpdate()) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_group_updated));
    } else if (isGroupQuit()) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_left_the_group));
    } else if (isKeyExchange()) {
      return emphasisAdded(context.getString(R.string.ConversationListItem_key_exchange_message));
    } else if (SmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_bad_encrypted_message));
    } else if (SmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session));
    } else if (SmsDatabase.Types.isEndSessionType(type)) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_secure_session_reset));
    } else if (MmsSmsColumns.Types.isLegacyType(type)) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (MmsSmsColumns.Types.isDraftMessageType(type)) {
      String draftText = context.getString(R.string.ThreadRecord_draft);
      return emphasisAdded(draftText + " " + getBody(), 0, draftText.length());
    } else if (SmsDatabase.Types.isOutgoingCall(type)) {
      return emphasisAdded(context.getString(org.btider.dediapp.R.string.ThreadRecord_called));
    } else if (SmsDatabase.Types.isIncomingCall(type)) {
      return emphasisAdded(context.getString(org.btider.dediapp.R.string.ThreadRecord_called_you));
    } else if (SmsDatabase.Types.isMissedCall(type)) {
      return emphasisAdded(context.getString(org.btider.dediapp.R.string.ThreadRecord_missed_call));
    } else if (SmsDatabase.Types.isJoinedType(type)) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_s_is_on_signal, getRecipient().toShortString()));
    } else if (SmsDatabase.Types.isExpirationTimerUpdate(type)) {
      String time = ExpirationUtil.getExpirationDisplayValue(context, (int) (getExpiresIn() / 1000));
      return emphasisAdded(context.getString(R.string.ThreadRecord_disappearing_message_time_updated_to_s, time));
    } else if (SmsDatabase.Types.isIdentityUpdate(type)) {
      if (getRecipient().isGroupRecipient()) return emphasisAdded(context.getString(R.string.ThreadRecord_safety_number_changed));
      else                                   return emphasisAdded(context.getString(R.string.ThreadRecord_your_safety_number_with_s_has_changed, getRecipient().toShortString()));
    } else if (SmsDatabase.Types.isIdentityVerified(type)) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_you_marked_verified));
    } else if (SmsDatabase.Types.isIdentityDefault(type)) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_you_marked_unverified));
    } else {
      if (TextUtils.isEmpty(getBody())) {
        return new SpannableString(emphasisAdded(context.getString(R.string.ThreadRecord_media_message)));
      } else {
        return new SpannableString(getBody());
      }
    }
  }

  private SpannableString emphasisAdded(String sequence) {
    return emphasisAdded(sequence, 0, sequence.length());
  }

  private SpannableString emphasisAdded(String sequence, int start, int end) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                      start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public long getCount() {
    return count;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public long getDate() {
    return getDateReceived();
  }

  public boolean isArchived() {
    return archived;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public long getLastSeen() {
    return lastSeen;
  }
}
