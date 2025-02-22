/*
 * Copyright (C) 2011 Whisper Systems
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

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.annimon.stream.Stream;

import org.btider.dediapp.attachments.DatabaseAttachment;
import org.btider.dediapp.database.DatabaseFactory;
import org.btider.dediapp.database.FastCursorRecyclerViewAdapter;
import org.btider.dediapp.database.MmsSmsColumns;
import org.btider.dediapp.database.MmsSmsDatabase;
import org.btider.dediapp.database.model.MessageRecord;
import org.btider.dediapp.database.model.MmsMessageRecord;
import org.btider.dediapp.mms.SlideDeck;
import org.btider.dediapp.recipients.Recipient;
import org.btider.dediapp.util.Conversions;
import org.btider.dediapp.util.DateUtils;
import org.btider.dediapp.util.LRUCache;
import org.btider.dediapp.util.StickyHeaderDecoration;
import org.btider.dediapp.util.Util;
import org.btider.dediapp.util.ViewUtil;
import org.btider.dediapp.ConversationAdapter.HeaderViewHolder;
import org.btider.dediapp.R;
import org.btider.dediapp.attachments.DatabaseAttachment;
import org.btider.dediapp.database.DatabaseFactory;
import org.btider.dediapp.database.FastCursorRecyclerViewAdapter;
import org.btider.dediapp.database.MmsSmsColumns;
import org.btider.dediapp.database.MmsSmsDatabase;
import org.btider.dediapp.database.model.MessageRecord;
import org.btider.dediapp.database.model.MmsMessageRecord;
import org.btider.dediapp.mms.GlideRequests;
import org.btider.dediapp.mms.SlideDeck;
import org.btider.dediapp.recipients.Recipient;
import org.btider.dediapp.util.Conversions;
import org.btider.dediapp.util.DateUtils;
import org.btider.dediapp.util.LRUCache;
import org.btider.dediapp.util.StickyHeaderDecoration;
import org.btider.dediapp.util.Util;
import org.btider.dediapp.util.ViewUtil;

import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A cursor adapter for a conversation thread.  Ultimately
 * used by ComposeMessageActivity to display a conversation
 * thread in a ListActivity.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationAdapter <V extends View & BindableConversationItem>
    extends FastCursorRecyclerViewAdapter<ConversationAdapter.ViewHolder, MessageRecord>
  implements StickyHeaderDecoration.StickyHeaderAdapter<HeaderViewHolder>
{

  private static final int MAX_CACHE_SIZE = 40;
  private static final String TAG = ConversationAdapter.class.getSimpleName();
  private final Map<String,SoftReference<MessageRecord>> messageRecordCache =
      Collections.synchronizedMap(new LRUCache<String, SoftReference<MessageRecord>>(MAX_CACHE_SIZE));

  private static final int MESSAGE_TYPE_OUTGOING           = 0;
  private static final int MESSAGE_TYPE_INCOMING           = 1;
  private static final int MESSAGE_TYPE_UPDATE             = 2;
  private static final int MESSAGE_TYPE_AUDIO_OUTGOING     = 3;
  private static final int MESSAGE_TYPE_AUDIO_INCOMING     = 4;
  private static final int MESSAGE_TYPE_THUMBNAIL_OUTGOING = 5;
  private static final int MESSAGE_TYPE_THUMBNAIL_INCOMING = 6;
  private static final int MESSAGE_TYPE_DOCUMENT_OUTGOING  = 7;
  private static final int MESSAGE_TYPE_DOCUMENT_INCOMING  = 8;

  private final Set<MessageRecord> batchSelected = Collections.synchronizedSet(new HashSet<MessageRecord>());

  private final @Nullable ItemClickListener clickListener;
  private final @NonNull  GlideRequests     glideRequests;
  private final @NonNull  Locale            locale;
  private final @NonNull
  Recipient recipient;
  private final @NonNull
  MmsSmsDatabase db;
  private final @NonNull  LayoutInflater    inflater;
  private final @NonNull  Calendar          calendar;
  private final @NonNull  MessageDigest     digest;

  private MessageRecord recordToPulseHighlight;

  protected static class ViewHolder extends RecyclerView.ViewHolder {
    public <V extends View & BindableConversationItem> ViewHolder(final @NonNull V itemView) {
      super(itemView);
    }

    @SuppressWarnings("unchecked")
    public <V extends View & BindableConversationItem> V getView() {
      return (V)itemView;
    }
  }


  static class HeaderViewHolder extends RecyclerView.ViewHolder {
    TextView textView;

    HeaderViewHolder(View itemView) {
      super(itemView);
      textView = ViewUtil.findById(itemView, R.id.text);
    }

    HeaderViewHolder(TextView textView) {
      super(textView);
      this.textView = textView;
    }

    public void setText(CharSequence text) {
      textView.setText(text);
    }
  }


  interface ItemClickListener extends BindableConversationItem.EventListener {
    void onItemClick(MessageRecord item);
    void onItemLongClick(MessageRecord item);
  }

  @SuppressWarnings("ConstantConditions")
  @VisibleForTesting
  ConversationAdapter(Context context, Cursor cursor) {
    super(context, cursor);
    try {
      this.glideRequests = null;
      this.locale        = null;
      this.clickListener = null;
      this.recipient     = null;
      this.inflater      = null;
      this.db            = null;
      this.calendar      = null;
      this.digest        = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError("SHA1 isn't supported!");
    }
  }

  public ConversationAdapter(@NonNull Context context,
                             @NonNull GlideRequests glideRequests,
                             @NonNull Locale locale,
                             @Nullable ItemClickListener clickListener,
                             @Nullable Cursor cursor,
                             @NonNull Recipient recipient)
  {
    super(context, cursor);

    try {
      this.glideRequests = glideRequests;
      this.locale        = locale;
      this.clickListener = clickListener;
      this.recipient     = recipient;
      this.inflater      = LayoutInflater.from(context);
      this.db            = DatabaseFactory.getMmsSmsDatabase(context);
      this.calendar      = Calendar.getInstance();
      this.digest        = MessageDigest.getInstance("SHA1");

      setHasStableIds(true);
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError("SHA1 isn't supported!");
    }
  }

  @Override
  public void changeCursor(Cursor cursor) {
    messageRecordCache.clear();
    super.cleanFastRecords();
    super.changeCursor(cursor);
  }

  @Override
  protected void onBindItemViewHolder(ViewHolder viewHolder, @NonNull MessageRecord messageRecord) {
    long start = System.currentTimeMillis();
    viewHolder.getView().bind(messageRecord, glideRequests, locale, batchSelected, recipient, messageRecord == recordToPulseHighlight);
    if (messageRecord == recordToPulseHighlight) {
      recordToPulseHighlight = null;
    }
    Log.w(TAG, "Bind time: " + (System.currentTimeMillis() - start));
  }

  @Override
  public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    long start = System.currentTimeMillis();
    final V itemView = ViewUtil.inflate(inflater, parent, getLayoutForViewType(viewType));
    itemView.setOnClickListener(view -> {
      if (clickListener != null) {
        clickListener.onItemClick(itemView.getMessageRecord());
      }
    });
    itemView.setOnLongClickListener(view -> {
      if (clickListener != null) {
        clickListener.onItemLongClick(itemView.getMessageRecord());
      }
      return true;
    });
    itemView.setEventListener(clickListener);
    Log.w(TAG, "Inflate time: " + (System.currentTimeMillis() - start));
    return new ViewHolder(itemView);
  }

  @Override
  public void onItemViewRecycled(ViewHolder holder) {
    holder.getView().unbind();
  }

  private @LayoutRes int getLayoutForViewType(int viewType) {
    switch (viewType) {
      case MESSAGE_TYPE_AUDIO_OUTGOING:
      case MESSAGE_TYPE_THUMBNAIL_OUTGOING:
      case MESSAGE_TYPE_DOCUMENT_OUTGOING:
      case MESSAGE_TYPE_OUTGOING:        return R.layout.conversation_item_sent;
      case MESSAGE_TYPE_AUDIO_INCOMING:
      case MESSAGE_TYPE_THUMBNAIL_INCOMING:
      case MESSAGE_TYPE_DOCUMENT_INCOMING:
      case MESSAGE_TYPE_INCOMING:        return R.layout.conversation_item_received;
      case MESSAGE_TYPE_UPDATE:          return R.layout.conversation_item_update;
      default: throw new IllegalArgumentException("unsupported item view type given to ConversationAdapter");
    }
  }

  @Override
  public int getItemViewType(@NonNull MessageRecord messageRecord) {
    if (messageRecord.isGroupAction() || messageRecord.isCallLog() || messageRecord.isJoined() ||
        messageRecord.isExpirationTimerUpdate() || messageRecord.isEndSession()                ||
        messageRecord.isIdentityUpdate() || messageRecord.isIdentityVerified()                 ||
        messageRecord.isIdentityDefault())
    {
      return MESSAGE_TYPE_UPDATE;
    } else if (hasAudio(messageRecord)) {
      if (messageRecord.isOutgoing()) return MESSAGE_TYPE_AUDIO_OUTGOING;
      else                            return MESSAGE_TYPE_AUDIO_INCOMING;
    } else if (hasDocument(messageRecord)) {
      if (messageRecord.isOutgoing()) return MESSAGE_TYPE_DOCUMENT_OUTGOING;
      else                            return MESSAGE_TYPE_DOCUMENT_INCOMING;
    } else if (hasThumbnail(messageRecord)) {
      if (messageRecord.isOutgoing()) return MESSAGE_TYPE_THUMBNAIL_OUTGOING;
      else                            return MESSAGE_TYPE_THUMBNAIL_INCOMING;
    } else if (messageRecord.isOutgoing()) {
      return MESSAGE_TYPE_OUTGOING;
    } else {
      return MESSAGE_TYPE_INCOMING;
    }
  }

  @Override
  protected boolean isRecordForId(@NonNull MessageRecord record, long id) {
    return record.getId() == id;
  }

  @Override
  public long getItemId(@NonNull Cursor cursor) {
    List<DatabaseAttachment> attachments        = DatabaseFactory.getAttachmentDatabase(getContext()).getAttachment(cursor);
    List<DatabaseAttachment> messageAttachments = Stream.of(attachments).filterNot(DatabaseAttachment::isQuote).toList();

    if (messageAttachments.size() > 0 && messageAttachments.get(0).getFastPreflightId() != null) {
      return Long.valueOf(messageAttachments.get(0).getFastPreflightId());
    }

    final String unique = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsColumns.UNIQUE_ROW_ID));
    final byte[] bytes  = digest.digest(unique.getBytes());
    return Conversions.byteArrayToLong(bytes);
  }

  @Override
  protected long getItemId(@NonNull MessageRecord record) {
    if (record.isOutgoing() && record.isMms()) {
      SlideDeck slideDeck = ((MmsMessageRecord)record).getSlideDeck();

      if (slideDeck.getThumbnailSlide() != null && slideDeck.getThumbnailSlide().getFastPreflightId() != null) {
        return Long.valueOf(slideDeck.getThumbnailSlide().getFastPreflightId());
      }
    }

    return record.getId();
  }

  @Override
  protected MessageRecord getRecordFromCursor(@NonNull Cursor cursor) {
    long   messageId = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.ID));
    String type      = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));

    final SoftReference<MessageRecord> reference = messageRecordCache.get(type + messageId);
    if (reference != null) {
      final MessageRecord record = reference.get();
      if (record != null) return record;
    }

    final MessageRecord messageRecord = db.readerFor(cursor).getCurrent();
    messageRecordCache.put(type + messageId, new SoftReference<>(messageRecord));

    return messageRecord;
  }

  public void close() {
    getCursor().close();
  }

  public int findLastSeenPosition(long lastSeen) {
    if (lastSeen <= 0)     return -1;
    if (!isActiveCursor()) return -1;

    int count = getItemCount() - (hasFooterView() ? 1 : 0);

    for (int i=(hasHeaderView() ? 1 : 0);i<count;i++) {
      MessageRecord messageRecord = getRecordForPositionOrThrow(i);

      if (messageRecord.isOutgoing() || messageRecord.getDateReceived() <= lastSeen) {
        return i;
      }
    }

    return -1;
  }

  public void toggleSelection(MessageRecord messageRecord) {
    if (!batchSelected.remove(messageRecord)) {
      batchSelected.add(messageRecord);
    }
  }

  public void clearSelection() {
    batchSelected.clear();
  }

  public Set<MessageRecord> getSelectedItems() {
    return Collections.unmodifiableSet(new HashSet<>(batchSelected));
  }

  public void pulseHighlightItem(int position) {
    if (position < getItemCount()) {
      recordToPulseHighlight = getRecordForPositionOrThrow(position);
      notifyItemChanged(position);
    }
  }

  private boolean hasAudio(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getAudioSlide() != null;
  }

  private boolean hasDocument(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getDocumentSlide() != null;
  }

  private boolean hasThumbnail(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide() != null;
  }


  @Override
  public long getHeaderId(int position) {
    if (!isActiveCursor())          return -1;
    if (isHeaderPosition(position)) return -1;
    if (isFooterPosition(position)) return -1;
    if (position >= getItemCount()) return -1;
    if (position < 0)               return -1;

    MessageRecord record = getRecordForPositionOrThrow(position);

    calendar.setTime(new Date(record.getDateSent()));
    return Util.hashCode(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR));
  }

  public long getReceivedTimestamp(int position) {
    if (!isActiveCursor())          return 0;
    if (isHeaderPosition(position)) return 0;
    if (isFooterPosition(position)) return 0;
    if (position >= getItemCount()) return 0;
    if (position < 0)               return 0;

    MessageRecord messageRecord = getRecordForPositionOrThrow(position);

    if (messageRecord.isOutgoing()) return 0;
    else                            return messageRecord.getDateReceived();
  }

  @Override
  public HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent) {
    return new HeaderViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.conversation_item_header, parent, false));
  }

  public HeaderViewHolder onCreateLastSeenViewHolder(ViewGroup parent) {
    return new HeaderViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.conversation_item_last_seen, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(HeaderViewHolder viewHolder, int position) {
    MessageRecord messageRecord = getRecordForPositionOrThrow(position);
    viewHolder.setText(DateUtils.getRelativeDate(getContext(), locale, messageRecord.getDateReceived()));
  }

  public void onBindLastSeenViewHolder(HeaderViewHolder viewHolder, int position) {
    viewHolder.setText(getContext().getResources().getQuantityString(R.plurals.ConversationAdapter_n_unread_messages, (position + 1), (position + 1)));
  }

  static class LastSeenHeader extends StickyHeaderDecoration {

    private final ConversationAdapter adapter;
    private final long                lastSeenTimestamp;

    LastSeenHeader(ConversationAdapter adapter, long lastSeenTimestamp) {
      super(adapter, false, false);
      this.adapter           = adapter;
      this.lastSeenTimestamp = lastSeenTimestamp;
    }

    @Override
    protected boolean hasHeader(RecyclerView parent, StickyHeaderAdapter stickyAdapter, int position) {
      if (!adapter.isActiveCursor()) {
        return false;
      }

      if (lastSeenTimestamp <= 0) {
        return false;
      }

      long currentRecordTimestamp  = adapter.getReceivedTimestamp(position);
      long previousRecordTimestamp = adapter.getReceivedTimestamp(position + 1);

      return currentRecordTimestamp > lastSeenTimestamp && previousRecordTimestamp < lastSeenTimestamp;
    }

    @Override
    protected int getHeaderTop(RecyclerView parent, View child, View header, int adapterPos, int layoutPos) {
      return parent.getLayoutManager().getDecoratedTop(child);
    }

    @Override
    protected HeaderViewHolder getHeader(RecyclerView parent, StickyHeaderAdapter stickyAdapter, int position) {
      HeaderViewHolder viewHolder = adapter.onCreateLastSeenViewHolder(parent);
      adapter.onBindLastSeenViewHolder(viewHolder, position);

      int widthSpec  = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
      int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

      int childWidth  = ViewGroup.getChildMeasureSpec(widthSpec, parent.getPaddingLeft() + parent.getPaddingRight(), viewHolder.itemView.getLayoutParams().width);
      int childHeight = ViewGroup.getChildMeasureSpec(heightSpec, parent.getPaddingTop() + parent.getPaddingBottom(), viewHolder.itemView.getLayoutParams().height);

      viewHolder.itemView.measure(childWidth, childHeight);
      viewHolder.itemView.layout(0, 0, viewHolder.itemView.getMeasuredWidth(), viewHolder.itemView.getMeasuredHeight());

      return viewHolder;
    }
  }

}

