import 'dart:async';
import 'dart:core';

import 'package:flutter/services.dart';

// Current day's accumulated values
enum _ActivityType { steps, cycling, walkRun, heartRate, flights }

class FlutterHealthFit {
  static const MethodChannel _channel =
      const MethodChannel('flutter_health_fit');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<bool> get authorize async {
    return await _channel.invokeMethod('requestAuthorization');
  }

  static Future<Map<dynamic, dynamic>> get getBasicHealthData async {
    return await _channel.invokeMethod('getBasicHealthData');
  }

//  static Future<Map<dynamic, dynamic>> get getHealthDataHistory async {
//    return await _channel.invokeMethod('getFitnessHistoy');
//  }

  static Future<Map<dynamic, dynamic>> getStepsHistory(int day) async {
    int date=day;
    if(date>0){
      date = -date+1;
    }
    return await _channel.invokeMethod('getStepHistory',{"day":date});  }

  static Future<double> getDaySteps(int day) async {
    return await _getActivityData(_ActivityType.steps, "count", day);
  }

  static Future<double> get getSteps async {
    return await _getActivityData(_ActivityType.steps, "count", -1);
  }

  static Future<double> get getWalkingAndRunningDistance async {
    return await _getActivityData(_ActivityType.walkRun, "m", -1);
  }

  static Future<double> get geCyclingDistance async {
    return await _getActivityData(_ActivityType.cycling, "m", -1);
  }

  static Future<double> get getFlights async {
    return await _getActivityData(_ActivityType.flights, "count", -1);
  }

  static Future<double> _getActivityData(
      _ActivityType activityType, String units, int day) async {
    var result;

    try {
      result = await _channel.invokeMethod('getActivity', {
        "name": activityType.toString().split(".").last,
        "units": units,
        "day": day,
      });
    } catch (e) {
      print(e.toString());
      return null;
    }

    if (result == null || result.isEmpty) {
      return null;
    }

    return result["value"];
  }
}
