package org.btider.dediapp.util;

import android.app.Activity;

import org.btider.dediapp.R;

public class DynamicNoActionBarTheme extends DynamicTheme {
  @Override
  protected int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals("dark")) return R.style.TextSecure_DarkNoActionBar;

    return R.style.TextSecure_LightNoActionBar;
  }
}
