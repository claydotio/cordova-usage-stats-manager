var exec = require('cordova/exec');

module.exports = {
  getUsageStatistics: function(success, error, interval, packageNames) {
    console.log("getUsageStatistics() :: " + interval);
  	var array = [interval, packageNames];
  	exec(success, error, "MyUsageStatsManager", "getUsageStatistics", array);
  },

	openPermissionSettings: function(success, error) {
		console.log("openPermissionSettings() :: ");
    	exec(success, error, "MyUsageStatsManager", "openPermissionSettings", null);
	}
};
