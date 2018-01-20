package com.mobileaccord.geopoll.plugins;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import android.widget.Toast;
import android.content.Context;


/**
 *
 */
public class MyUsageStatsManager extends CordovaPlugin {

    private static final String LOG_TAG = "MyUsageStatsManager";

    UsageStatsManager mUsageStatsManager;

    public PackageManager packMan;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        mUsageStatsManager = (UsageStatsManager) this.cordova.getActivity().getApplicationContext().getSystemService("usagestats"); //Context.USAGE_STATS_SERVICE
        packMan = this.cordova.getActivity().getApplicationContext().getPackageManager();
        if (action.equals("getUsageStatistics")) {
            String interval = args.getString(0);
            JSONArray packageNames = args.getJSONArray(1);
            this.getUsageStatistics(interval, packageNames, callbackContext);
            return true;
        }else if(action.equals("openPermissionSettings")){
            this.openPermissionSettings(callbackContext);
            return true;
        }
        return false;
    }

    private Boolean getIsUsageStatsEnabled() {
       Context context = this.cordova.getActivity().getApplicationContext();
       if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
           return false;
       }

       final AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

       if (appOpsManager == null) {
           return false;
       }

       final int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
       if (mode != AppOpsManager.MODE_ALLOWED) {
           return false;
       }

       // Verify that access is possible. Some devices "lie" and return MODE_ALLOWED even when it's not.
       final long now = System.currentTimeMillis();
       final UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
       final List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 3600, now);
       return (stats != null && !stats.isEmpty());
    }

    private void getUsageStatistics(final String interval, final JSONArray packageNames, final CallbackContext callbackContext) {
        Runnable runnable = new Runnable() {
          public void run() {
            try{
                Boolean isUsageStatsEnabled = getIsUsageStatsEnabled();
                if(!isUsageStatsEnabled) {
                  callbackContext.success();
                  return;
                }

                String packageNamesStr = packageNames.toString();

                Log.i(LOG_TAG, interval);
                StatsUsageInterval statsUsageInterval = StatsUsageInterval.getValue(interval);
                List<UsageStats> usageStatsList = new ArrayList<UsageStats>();
                if (statsUsageInterval != null) {
                    usageStatsList = queryUsageStatistics(statsUsageInterval.mInterval);
                    // Collections.sort(usageStatsList, new LastTimeLaunchedComparatorDesc());
                }

                JSONArray jsonArray = new JSONArray();
                for (UsageStats stat : usageStatsList){
                  if(packageNamesStr.contains("\"" + stat.getPackageName() + "\"")) {
                    JSONObject obj = toJSON(stat);
                    jsonArray.put(obj);
                  }
                }

                String result = jsonArray.toString();
                callbackContext.success(jsonArray);

            }catch (Exception e){
                e.printStackTrace();
                callbackContext.error(e.toString());
            }
          }
        };
        this.cordova.getThreadPool().execute(runnable);
    }


    /**
     * Returns the List of UsageStats including the time span specified by the
     * intervalType argument.
     *
     * @param intervalType The time interval by which the stats are aggregated.
     *                     Corresponding to the value of {@link UsageStatsManager}.
     *                     E.g. {@link UsageStatsManager#INTERVAL_DAILY}, {@link
     *                     UsageStatsManager#INTERVAL_WEEKLY},
     *
     * @return A list of {@link android.app.usage.UsageStats}.
     */
    public List<UsageStats> queryUsageStatistics(int intervalType) {
        // Get the app statistics since 2 weeks ago from the current time.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -14);
        List<UsageStats> queryUsageStats = mUsageStatsManager.queryUsageStats(intervalType, cal.getTimeInMillis(), System.currentTimeMillis());
        return queryUsageStats;
    }

    /**
     * The {@link Comparator} to sort a collection of {@link UsageStats} sorted by the timestamp
     * last time the app was used in the descendant order.
     */
    private static class LastTimeLaunchedComparatorDesc implements Comparator<UsageStats> {
        @Override
        public int compare(UsageStats left, UsageStats right) {
            return Long.compare(right.getLastTimeUsed(), left.getLastTimeUsed());
        }
    }

    /**
     * Enum represents the intervals for {@link android.app.usage.UsageStatsManager} so that
     * values for intervals can be found by a String representation.
     *
     */
    //VisibleForTesting
    static enum StatsUsageInterval {
        DAILY("Daily", UsageStatsManager.INTERVAL_DAILY),
        WEEKLY("Weekly", UsageStatsManager.INTERVAL_WEEKLY),
        MONTHLY("Monthly", UsageStatsManager.INTERVAL_MONTHLY),
        YEARLY("Yearly", UsageStatsManager.INTERVAL_YEARLY);

        private int mInterval;
        private String mStringRepresentation;

        StatsUsageInterval(String stringRepresentation, int interval) {
            mStringRepresentation = stringRepresentation;
            mInterval = interval;
        }

        static StatsUsageInterval getValue(String stringRepresentation) {
            for (StatsUsageInterval statsUsageInterval : values()) {
                if (statsUsageInterval.mStringRepresentation.equals(stringRepresentation)) {
                    return statsUsageInterval;
                }
            }
            return null;
        }
    }

    /**
     * Converts UsageStats into a JSONObject
     * @param usageStats
     * @return
     * @throws Exception
     */
    public JSONObject toJSON(UsageStats usageStats) throws Exception{
        JSONObject object= new JSONObject();
        object.put("PackageName", usageStats.getPackageName());
        // object.put("AppName", getAppName(usageStats.getPackageName()));
        object.put("FirstTimeStamp", usageStats.getFirstTimeStamp());
        object.put("LastTimeStamp", usageStats.getLastTimeStamp());
        object.put("LastTimeUsed", usageStats.getLastTimeUsed());
        object.put("TotalTimeInForeground", usageStats.getTotalTimeInForeground());
        return object;
    }

    /**
     * Launch UsageStatsManager settings
     * @return
     */
    private void openPermissionSettings(CallbackContext callbackContext){
        try {
            Context context = this.cordova.getActivity().getApplicationContext();
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            callbackContext.success("OK");

        } catch(Exception e){
            e.printStackTrace();
            callbackContext.error(e.toString());
        }

    }

    public String getAppName(String packageName) {
        ApplicationInfo ai;

        try {
            ai = packMan.getApplicationInfo(packageName, 0);
        } catch (Exception e) {
            ai = null;
        }

        return (String) (ai != null ? packMan.getApplicationLabel(ai) : "(unknown)");
    }
}
