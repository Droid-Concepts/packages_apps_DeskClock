/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.alarmclock;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.Toast;

/**
 * Manages each alarm
 */
public class SetAlarm extends PreferenceActivity
        implements Alarms.AlarmSettings, TimePickerDialog.OnTimeSetListener {

    private EditTextPreference mLabel;
    private Preference mTimePref;
    private AlarmPreference mAlarmPref;
    private CheckBoxPreference mVibratePref;
    private RepeatPreference mRepeatPref;
    private MenuItem mDeleteAlarmItem;
    private MenuItem mTestAlarmItem;

    private int mId;
    private int mHour;
    private int mMinutes;

    private boolean mReportAlarmCalled;

    /**
     * Set an alarm.  Requires an Alarms.ID to be passed in as an
     * extra
     */
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.alarm_prefs);

        // Get each preference so we can retrieve the value later.
        mLabel = (EditTextPreference) findPreference("label");
        mLabel.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference p,
                            Object newValue) {
                        // Set the summary based on the new label.
                        p.setSummary((String) newValue);
                        return true;
                    }
                });
        mTimePref = findPreference("time");
        mAlarmPref = (AlarmPreference) findPreference("alarm");
        mVibratePref = (CheckBoxPreference) findPreference("vibrate");
        mRepeatPref = (RepeatPreference) findPreference("setRepeat");

        Intent i = getIntent();
        mId = i.getIntExtra(Alarms.ID, -1);
        if (Log.LOGV) {
            Log.v("In SetAlarm, alarm id = " + mId);
        }

        mReportAlarmCalled = false;
        /* load alarm details from database */
        Alarms.getAlarm(getContentResolver(), this, mId);
        /* This should never happen, but does occasionally with the monkey.
         * I believe it's a race condition where a deleted alarm is opened
         * before the alarm list is refreshed. */
        if (!mReportAlarmCalled) {
            Log.e("reportAlarm never called!");
            finish();
        }

        // We have to do this to get the save/cancel buttons to highlight on
        // their own.
        getListView().setItemsCanFocus(true);

        // Grab the content view so we can modify it.
        FrameLayout content = (FrameLayout) getWindow().getDecorView()
                .findViewById(com.android.internal.R.id.content);

        // Get the main ListView and remove it from the content view.
        ListView lv = getListView();
        content.removeView(lv);

        // Create the new LinearLayout that will become the content view and
        // make it vertical.
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);

        // Have the ListView expand to fill the screen minus the save/cancel
        // buttons.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT);
        lp.weight = 1;
        ll.addView(lv, lp);

        // Inflate the buttons onto the LinearLayout.
        View v = LayoutInflater.from(this).inflate(
                R.layout.save_cancel_alarm, ll);

        // Attach actions to each button.
        Button b = (Button) v.findViewById(R.id.alarm_save);
        b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    saveAlarm();
                    finish();
                }
        });
        b = (Button) v.findViewById(R.id.alarm_cancel);
        b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    finish();
                }
        });

        // Replace the old content view with our new one.
        setContentView(ll);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mTimePref) {
            new TimePickerDialog(this, this, mHour, mMinutes,
                    DateFormat.is24HourFormat(this)).show();
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        mHour = hourOfDay;
        mMinutes = minute;
        updateTime();
    }

    /**
     * Alarms.AlarmSettings implementation.  Database feeds current
     * settings in through this call
     */
    public void reportAlarm(
            int idx, boolean enabled, int hour, int minutes,
            Alarms.DaysOfWeek daysOfWeek, boolean vibrate, String label,
            String alert) {

        mLabel.setText(label);
        mLabel.setSummary(label);
        mHour = hour;
        mMinutes = minutes;
        mRepeatPref.setDaysOfWeek(daysOfWeek);
        mVibratePref.setChecked(vibrate);

        Uri alertUri = null;
        if (Alarms.ALARM_ALERT_SILENT.equals(alert)) {
            if (Log.LOGV) {
                Log.v("reportAlarm: silent alert");
            }
        } else {
            if (alert != null && alert.length() != 0) {
                alertUri = Uri.parse(alert);
            }

            // If the database alert is null or it failed to parse, use the
            // default alert.
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_ALARM);
            }

            if (Log.LOGV) {
                Log.v("reportAlarm alert: " + alert + " uri: " + alertUri);
            }
        }

        // Give the alert uri to the preference.
        mAlarmPref.setAlert(alertUri);

        updateTime();

        mReportAlarmCalled = true;
    }

    private void updateTime() {
        if (Log.LOGV) {
            Log.v("updateTime " + mId);
        }
        mTimePref.setSummary(Alarms.formatTime(this, mHour, mMinutes,
                mRepeatPref.getDaysOfWeek()));
    }

    private void saveAlarm() {
        final String alert = mAlarmPref.getAlertString();
        Alarms.setAlarm(this, mId, true, mHour, mMinutes,
                mRepeatPref.getDaysOfWeek(), mVibratePref.isChecked(),
                mLabel.getText(), alert);

        popAlarmSetToast(this, mHour, mMinutes, mRepeatPref.getDaysOfWeek());
    }

    /**
     * Write alarm out to persistent store and pops toast if alarm
     * enabled
     */
    private static void saveAlarm(
            Context context, int id, boolean enabled, int hour, int minute,
            Alarms.DaysOfWeek daysOfWeek, boolean vibrate, String label,
            String alert, boolean popToast) {
        if (Log.LOGV) Log.v("** saveAlarm " + id + " " + label + " " + enabled
                + " " + hour + " " + minute + " vibe " + vibrate);

        // Fix alert string first
        Alarms.setAlarm(context, id, enabled, hour, minute, daysOfWeek, vibrate,
                label, alert);

        if (enabled && popToast) {
            popAlarmSetToast(context, hour, minute, daysOfWeek);
        }
    }

    /**
     * Display a toast that tells the user how long until the alarm
     * goes off.  This helps prevent "am/pm" mistakes.
     */
    static void popAlarmSetToast(Context context, int hour, int minute,
                                 Alarms.DaysOfWeek daysOfWeek) {

        String toastText = formatToast(context, hour, minute, daysOfWeek);
        Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_LONG);
        ToastMaster.setToast(toast);
        toast.show();
    }

    /**
     * format "Alarm set for 2 days 7 hours and 53 minutes from
     * now"
     */
    static String formatToast(Context context, int hour, int minute,
                              Alarms.DaysOfWeek daysOfWeek) {
        long alarm = Alarms.calculateAlarm(hour, minute,
                                           daysOfWeek).getTimeInMillis();
        long delta = alarm - System.currentTimeMillis();;
        long hours = delta / (1000 * 60 * 60);
        long minutes = delta / (1000 * 60) % 60;
        long days = hours / 24;
        hours = hours % 24;

        String daySeq = (days == 0) ? "" :
                (days == 1) ? context.getString(R.string.day) :
                context.getString(R.string.days, Long.toString(days));

        String minSeq = (minutes == 0) ? "" :
                (minutes == 1) ? context.getString(R.string.minute) :
                context.getString(R.string.minutes, Long.toString(minutes));

        String hourSeq = (hours == 0) ? "" :
                (hours == 1) ? context.getString(R.string.hour) :
                context.getString(R.string.hours, Long.toString(hours));

        boolean dispDays = days > 0;
        boolean dispHour = hours > 0;
        boolean dispMinute = minutes > 0;

        String ret;
        if (!(dispDays || dispHour || dispMinute)) {
            ret = context.getString(R.string.subminute);
        } else {
            String parts[] = new String[5];
            parts[0] = daySeq;
            parts[1] = !dispDays ? "" :
                    dispHour && dispMinute ? context.getString(R.string.space) :
                    !dispHour && !dispMinute ? "" :
                    context.getString(R.string.and);
            parts[2] = dispHour ? hourSeq : "";
            parts[3] = dispHour && dispMinute ? context.getString(R.string.and) : "";
            parts[4] = dispMinute ? minSeq : "";
            ret = context.getString(R.string.combiner, (Object[])parts);
        }

        ret = context.getString(R.string.alarm_set, ret);
        /* if (Log.LOGV) Log.v("** TOAST daySeq " + daySeq + " hourSeq " + hourSeq +
           " minSeq " + minSeq + " ret " + ret); */
        return ret;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        mDeleteAlarmItem = menu.add(0, 0, 0, R.string.delete_alarm);
        mDeleteAlarmItem.setIcon(android.R.drawable.ic_menu_delete);

        if (AlarmClock.DEBUG) {
            mTestAlarmItem = menu.add(0, 0, 0, "test alarm");
        }

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == mDeleteAlarmItem) {
            Alarms.deleteAlarm(this, mId);
            finish();
            return true;
        }
        if (AlarmClock.DEBUG) {
            if (item == mTestAlarmItem) {
                setTestAlarm();
                return true;
            }
        }

        return false;
    }


    /**
     * Test code: this is disabled for production build.  Sets
     * this alarm to go off on the next minute
     */
    void setTestAlarm() {

        // start with now
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());

        int nowHour = c.get(java.util.Calendar.HOUR_OF_DAY);
        int nowMinute = c.get(java.util.Calendar.MINUTE);

        int minutes = (nowMinute + 1) % 60;
        int hour = nowHour + (nowMinute == 0 ? 1 : 0);

        saveAlarm(this, mId, true, hour, minutes, mRepeatPref.getDaysOfWeek(),
                true, mLabel.getText(), mAlarmPref.getAlertString(), true);
    }

}