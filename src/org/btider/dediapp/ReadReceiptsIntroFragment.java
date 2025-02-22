package org.btider.dediapp;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.btider.dediapp.jobs.MultiDeviceReadReceiptUpdateJob;
import org.btider.dediapp.util.TextSecurePreferences;
import org.btider.dediapp.util.ViewUtil;
import org.btider.dediapp.R;
import org.btider.dediapp.jobs.MultiDeviceReadReceiptUpdateJob;
import org.btider.dediapp.util.TextSecurePreferences;
import org.btider.dediapp.util.ViewUtil;

public class ReadReceiptsIntroFragment extends Fragment {

  public static ReadReceiptsIntroFragment newInstance() {
    ReadReceiptsIntroFragment fragment = new ReadReceiptsIntroFragment();
    Bundle args = new Bundle();
    fragment.setArguments(args);
    return fragment;
  }

  public ReadReceiptsIntroFragment() {}

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View         v          = inflater.inflate(R.layout.experience_upgrade_preference_fragment, container, false);
    SwitchCompat preference = ViewUtil.findById(v, R.id.preference);

    preference.setChecked(TextSecurePreferences.isReadReceiptsEnabled(getContext()));
    preference.setOnCheckedChangeListener((buttonView, isChecked) -> {
      TextSecurePreferences.setReadReceiptsEnabled(getContext(), isChecked);
      ApplicationContext.getInstance(getContext())
                        .getJobManager()
                        .add(new MultiDeviceReadReceiptUpdateJob(getContext(), isChecked));
    });

    return v;
  }
}
