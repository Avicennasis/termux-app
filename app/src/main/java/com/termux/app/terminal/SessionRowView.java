package com.termux.app.terminal;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.termux.R;

/** A native list row: selection has both a checkmark and an accessibility state. */
public final class SessionRowView extends LinearLayout implements Checkable {
    private boolean mChecked;

    public SessionRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void bind(int number, String name, String title, boolean running, int exitStatus) {
        String heading = !TextUtils.isEmpty(name) ? name
            : !TextUtils.isEmpty(title) ? title : getContext().getString(R.string.avic_session_shell);
        String status = running ? getContext().getString(R.string.avic_session_running)
            : getContext().getString(R.string.avic_session_exited, exitStatus);
        String detail = !TextUtils.isEmpty(name) && !TextUtils.isEmpty(title)
            ? status + " · " + title : status;
        TextView numberView = findViewById(R.id.session_number);
        TextView headingView = findViewById(R.id.session_title);
        TextView detailView = findViewById(R.id.session_subtitle);
        numberView.setText(Integer.toString(number));
        headingView.setText(heading);
        detailView.setText(detail);
        detailView.setTextColor(ContextCompat.getColor(getContext(),
            running ? R.color.avic_muted : R.color.avic_amber));
        headingView.setTextColor(ContextCompat.getColor(getContext(),
            running || exitStatus == 0 ? R.color.avic_text : R.color.avic_error));
        setContentDescription(getContext().getString(R.string.avic_session_accessibility,
            number, heading, detail));
    }

    @Override
    public void setChecked(boolean checked) {
        mChecked = checked;
        setActivated(checked);
        ViewCompat.setStateDescription(this, checked
            ? getContext().getString(R.string.avic_session_current) : null);
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void toggle() {
        setChecked(!mChecked);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(mChecked);
        info.setClassName("android.widget.RadioButton");
    }
}
