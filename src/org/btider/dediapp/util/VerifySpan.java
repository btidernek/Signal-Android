package org.btider.dediapp.util;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.text.style.ClickableSpan;
import android.view.View;

import org.btider.dediapp.crypto.IdentityKeyParcelable;
import org.btider.dediapp.database.Address;
import org.btider.dediapp.database.documents.IdentityKeyMismatch;
import org.btider.dediapp.VerifyIdentityActivity;
import org.btider.dediapp.crypto.IdentityKeyParcelable;
import org.btider.dediapp.database.Address;
import org.btider.dediapp.database.documents.IdentityKeyMismatch;
import org.whispersystems.libsignal.IdentityKey;

public class VerifySpan extends ClickableSpan {

  private final Context     context;
  private final Address address;
  private final IdentityKey identityKey;

  public VerifySpan(@NonNull Context context, @NonNull IdentityKeyMismatch mismatch) {
    this.context     = context;
    this.address     = mismatch.getAddress();
    this.identityKey = mismatch.getIdentityKey();
  }

  public VerifySpan(@NonNull Context context, @NonNull Address address, @NonNull IdentityKey identityKey) {
    this.context     = context;
    this.address     = address;
    this.identityKey = identityKey;
  }

  @Override
  public void onClick(View widget) {
    Intent intent = new Intent(context, VerifyIdentityActivity.class);
    intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, address);
    intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(identityKey));
    intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, false);
    context.startActivity(intent);
  }
}
