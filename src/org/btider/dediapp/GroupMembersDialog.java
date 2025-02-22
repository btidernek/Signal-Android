package org.btider.dediapp;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;

import org.btider.dediapp.database.Address;
import org.btider.dediapp.database.DatabaseFactory;
import org.btider.dediapp.recipients.Recipient;
import org.btider.dediapp.util.Util;
import org.btider.dediapp.R;
import org.btider.dediapp.database.Address;
import org.btider.dediapp.database.DatabaseFactory;
import org.btider.dediapp.recipients.Recipient;
import org.btider.dediapp.util.TextSecurePreferences;
import org.btider.dediapp.util.Util;

import java.util.LinkedList;
import java.util.List;

public class GroupMembersDialog extends AsyncTask<Void, Void, List<Recipient>> {

  private static final String TAG = GroupMembersDialog.class.getSimpleName();

  private final Recipient  recipientMy;
  private final Context    context;
  private final String groupID;

  public GroupMembersDialog(Context context, Recipient recipient, String groupID) {
    this.recipientMy = recipient;
    this.context   = context;
    this.groupID = groupID;
  }

  @Override
  public void onPreExecute() {}

  @Override
  protected List<Recipient> doInBackground(Void... params) {
    return DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipientMy.getAddress().toGroupString(), true);
  }

  @Override
  public void onPostExecute(List<Recipient> members) {
    GroupMembers groupMembers = new GroupMembers(members);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.ConversationActivity_group_members);
    builder.setIconAttribute(R.attr.group_members_dialog_icon);
    builder.setCancelable(true);
    builder.setItems(groupMembers.getRecipientStrings(), new GroupMembersOnClickListener(context, groupMembers));
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  public void display() {
    executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private static class GroupMembersOnClickListener implements DialogInterface.OnClickListener {
    private final GroupMembers groupMembers;
    private final Context      context;

    public GroupMembersOnClickListener(Context context, GroupMembers members) {
      this.context      = context;
      this.groupMembers = members;
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int item) {
      Recipient recipient = groupMembers.get(item);

      if (recipient.getContactUri() != null) {
        Intent intent = new Intent(context, RecipientPreferenceActivity.class);
        intent.putExtra(RecipientPreferenceActivity.ADDRESS_EXTRA, recipient.getAddress());

        context.startActivity(intent);
      } else {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        if (recipient.getAddress().isEmail()) {
          intent.putExtra(ContactsContract.Intents.Insert.EMAIL, recipient.getAddress().toEmailString());
        } else {
          intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getAddress().toPhoneString());
        }
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        context.startActivity(intent);
      }
    }
  }

  /**
   * Wraps a List of Recipient (just like @class Recipients),
   * but with focus on the order of the Recipients.
   * So that the order of the RecipientStrings[] matches
   * the internal order.
   *
   * @author Christoph Haefner
   */
  private class GroupMembers {
    private final String TAG = GroupMembers.class.getSimpleName();

    private final LinkedList<Recipient> members = new LinkedList<>();

    public GroupMembers(List<Recipient> recipients) {
      for (Recipient recipient : recipients) {
        if (isLocalNumber(recipient)) {
          members.push(recipient);
        } else {
          members.add(recipient);
        }
      }
    }

    public String[] getRecipientStrings() {
      List<String> recipientStrings = new LinkedList<>();

      for (Recipient recipient : members) {
        if (isLocalNumber(recipient)) {
          String name = context.getString(R.string.GroupMembersDialog_me);
          try{
            boolean admin = false;
            List<Address> addresses = DatabaseFactory.getGroupDatabase(context).getAdmins(groupID);
            for(Address address : addresses){
              if(address.equals(recipient.getAddress())){
                admin = true;
              }
            }
            if(admin)
              name = name + " ("+context.getString(R.string.group_admin)+")";
          }catch (AssertionError e){}

          recipientStrings.add(name);

        } else {
          String name = recipient.toShortString();

          if (recipient.getName() == null && !TextUtils.isEmpty(recipient.getProfileName())) {


            name += " ~" + recipient.getProfileName();

          }

          if(recipient != null){
            try{
              boolean admin = false;
                List<Address> addresses = DatabaseFactory.getGroupDatabase(context).getAdmins(groupID);
              for(Address address : addresses){
                if(address.equals(recipient.getAddress())){
                  admin = true;
                }
              }
              if(admin)
                name = name + " ("+context.getString(R.string.group_admin)+")";
            }catch (AssertionError e){}
          }

          recipientStrings.add(name);
        }
      }

      return recipientStrings.toArray(new String[members.size()]);
    }

    public Recipient get(int index) {
      return members.get(index);
    }

    private boolean isLocalNumber(Recipient recipient) {
      return Util.isOwnNumber(context, recipient.getAddress());
    }
  }
}
