var exec = require('cordova/exec');

exports.initialize = function(appToken,userID, success, error) {
  exec(success, error, "FaceBidEnrollment", "initializeZoom", [appToken,userID]);
};

exports.enroll = function(success, error) {
  exec(success, error, "FaceBidEnrollment", "enrollWithZoom", []);
}