package com.metaflow.flutterhealthfit

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.PluginRegistry.Registrar

import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.tasks.*
import io.flutter.plugin.common.PluginRegistry
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.xml.datatype.DatatypeConstants.DAYS
//import android.renderscript.Element.DataType




class FlutterHealthFitPlugin(private val activity: Activity) : MethodCallHandler, PluginRegistry.ActivityResultListener {

    companion object {
        const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1

        val dataType: DataType = DataType.TYPE_STEP_COUNT_DELTA
        val aggregatedDataType: DataType = DataType.AGGREGATE_STEP_COUNT_DELTA

        val TAG = FlutterHealthFitPlugin::class.java.simpleName

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val plugin = FlutterHealthFitPlugin(registrar.activity())
            registrar.addActivityResultListener(plugin)

            val channel = MethodChannel(registrar.messenger(), "flutter_health_fit")
            channel.setMethodCallHandler(plugin)
        }
    }

    private var deferredResult: Result? = null

    override fun onMethodCall(call: MethodCall, result: Result) {

        when (call.method) {
            "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")

            "requestAuthorization" -> connect(result)

//            "getBasicHealthData" -> result.success(HashMap<String, String>())
            "getBasicHealthData" -> getFitnessHistoy(result)


//            "getFitnessHistoy" -> getFitnessHistoy(result)

            "getActivity" -> {
                val name = call.argument<String>("name")

                when (name) {
                    "steps" -> getStepsTotalWithRange(result,-14)

                    else -> {
                        val map = HashMap<String, Double>()
//                        map["value"] = 0.0
                        result.success(map)
                    }
                }
            }

            else -> result.notImplemented()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                recordData { success ->
                    Log.i(TAG, "Record data success: $success!")

                    if (success)
                        deferredResult?.success(true)
                    else
                        deferredResult?.error("no record", "Record data operation denied", null)

                    deferredResult = null
                }
            } else {
                deferredResult?.error("canceled", "User cancelled or app not authorized", null)
                deferredResult = null
            }

            return true
        }

        return false
    }

    private fun connect(result: Result) {
        val fitnessOptions = getFitnessOptions()

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(activity), fitnessOptions)) {
            deferredResult = result

            GoogleSignIn.requestPermissions(
                    activity,
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(activity),
                    fitnessOptions)
            result.success(true)

        } else {
            result.success(true)
        }
    }

    private fun recordData(callback: (Boolean) -> Unit) {
        Fitness.getRecordingClient(activity, GoogleSignIn.getLastSignedInAccount(activity)!!)
                .subscribe(dataType)
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
    }

    private fun getYesterdaysStepsTotal(result: Result) {
        val gsa = GoogleSignIn.getAccountForExtension(activity, getFitnessOptions())

        val startCal = GregorianCalendar()
        startCal.add(Calendar.DAY_OF_YEAR, -1)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)

//        val c = Calendar.instance;
        val endCal = GregorianCalendar(
                startCal.get(Calendar.YEAR),
                startCal.get(Calendar.MONTH),
                startCal.get(Calendar.DAY_OF_MONTH),
                23,
                59)

        val request = DataReadRequest.Builder()
                .aggregate(dataType, aggregatedDataType)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startCal.timeInMillis, endCal.timeInMillis, TimeUnit.MILLISECONDS)
                .build()

        val response = Fitness.getHistoryClient(activity, gsa).readData(request)

        val dateFormat = DateFormat.getDateInstance()
        val dayString = dateFormat.format(Date(startCal.timeInMillis))

        Thread {
            try {
                val readDataResult = Tasks.await<DataReadResponse>(response)
                Log.d(TAG, "buckets count: ${readDataResult.buckets.size}")

                if (!readDataResult.buckets.isEmpty()) {
                    val dp = readDataResult.buckets[0].dataSets[0].dataPoints[0]
                    val count = dp.getValue(aggregatedDataType.fields[0])

                    Log.d(TAG, "returning $count steps for $dayString")
                    val map = HashMap<String, Double>()
                    map["value"] = count.asInt().toDouble()

                    result.success(map)
                } else {
                    result.error("No data", "No data found for $dayString", null)
                }
            } catch (e: Throwable) {
                result.error("failed", e.message, null)
            }

        }.start()
    }

    private fun getStepsTotalWithRange(result: Result,date: Int){
        val gsa = GoogleSignIn.getAccountForExtension(activity, getFitnessOptions())

        val startCal = GregorianCalendar()
        startCal.add(Calendar.DAY_OF_YEAR, date)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)

//        val c = Calendar.instance;
        val endCal = GregorianCalendar()
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)

        val dateFormat = DateFormat.getDateInstance()

        Log.i(TAG, "Range Start: " + dateFormat.format(Date(startCal.timeInMillis)));
        Log.i(TAG, "Range End: " + dateFormat.format(Date(endCal.timeInMillis)));

        val request = DataReadRequest.Builder()
                .aggregate(dataType, aggregatedDataType)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startCal.timeInMillis, endCal.timeInMillis, TimeUnit.MILLISECONDS)
                .build()

        val response = Fitness.getHistoryClient(activity, gsa).readData(request)

        val dayString = dateFormat.format(Date(startCal.timeInMillis))


        Thread {
            try {
                val readDataResult = Tasks.await<DataReadResponse>(response)
                Log.d(TAG, "buckets count: ${readDataResult.buckets.size}")
                val map = HashMap<String, Double>()
                val dateFormat = DateFormat.getDateInstance()

                if (!readDataResult.buckets.isEmpty()) {
                    var i = 0
                    for (item in readDataResult.buckets) {
                        val dayString = dateFormat.format(Date(startCal.timeInMillis+86400000*(i)))
                        Log.d(TAG,"buckets "+item)
                        try {
                            val dp = item.dataSets[0].dataPoints[0]
                            val count = dp.getValue(aggregatedDataType.fields[0])

                            Log.d(TAG, "returning $count steps for $dayString")
                            map["value"] = count.asInt().toDouble()
                        }catch (e :Throwable)
                        {
                            Log.d(TAG, "returning 0 steps for $dayString")

                            map["value"] = 0.0
                        }
                        finally {
                            i++
                        }

                    }
                    result.success(map)
                } else {
                    result.error("No data", "No data found for $dayString", null)
                }
            } catch (e: Throwable) {
                result.error("failed", e.message, null)
            }

        }.start()


    }
/*
    private fun getStepsTotalOnDate(startCal: GregorianCalendar) {
        Log.i(TAG,"getStepsTotalOnDate")
        val gsa = GoogleSignIn.getAccountForExtension(activity, getFitnessOptions())

//        date = -kotlin.math.abs(date)

//        var cal = Calendar.getInstance();
//        val now = Date();
////        cal.setTime(now);
//        cal.time = now;
//        val endTime = cal.timeInMillis;
//        cal.add(Calendar.WEEK_OF_YEAR, -1);
//        val startTime = cal.timeInMillis;

//        java.text.DateFormat dateFormat = getDateInstance();


//
//        val startCal = GregorianCalendar()
//        startCal.add(Calendar.DAY_OF_YEAR, -1)
//        startCal.set(Calendar.HOUR_OF_DAY, 0)
//        startCal.set(Calendar.MINUTE, 0)
//        startCal.set(Calendar.SECOND, 0)

//        val c = Calendar.instance;
        val endCal = GregorianCalendar(
                startCal.get(Calendar.YEAR),
                startCal.get(Calendar.MONTH),
                startCal.get(Calendar.DAY_OF_MONTH),
                23,
                59)

        val dateFormat = DateFormat.getInstance();
        Log.i(TAG, "Range Start: " + dateFormat.format(Date(startCal.timeInMillis)));
        Log.i(TAG, "Range End: " + dateFormat.format(Date(endCal.timeInMillis)));

        val request = DataReadRequest.Builder()
                .aggregate(dataType, aggregatedDataType)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startCal.timeInMillis, endCal.timeInMillis, TimeUnit.MILLISECONDS)
                .build()

        val response = Fitness.getHistoryClient(activity, gsa).readData(request)

//        val dateFormat = DateFormat.getDateInstance()
        val dayString = dateFormat.format(Date(startCal.timeInMillis))

        Thread {
            try {
                val readDataResult = Tasks.await<DataReadResponse>(response)
                Log.d(TAG, "buckets count: ${readDataResult.buckets.size}")

                if (!readDataResult.buckets.isEmpty()) {
                    val dp = readDataResult.buckets[0].dataSets[0].dataPoints[0]
                    val count = dp.getValue(aggregatedDataType.fields[0])

                    Log.d(TAG, "returning $count steps for $dayString")
                    val map = HashMap<String, Double>()
                    map["value"] = count.asInt().toDouble()

                    return@Thread count.asInt().toDouble()

//                    result.success(map)
                } else {
//                    result.error("No data", "No data found for $dayString", null)
                }
            } catch (e: Throwable) {
//                result.error("failed", e.message, null)
            }

        }.start()

        return 0.0
    }
*/


    private fun getFitnessHistoy(result: Result) {
        Log.d(TAG, "getFitnessHistoy")

        val gsa = GoogleSignIn.getAccountForExtension(activity, getFitnessOptions())

//        var cal = Calendar.getInstance();
//        val now = Date();
////        cal.setTime(now);
//        cal.time = now;
//        val endTime = cal.timeInMillis;
//        cal.add(Calendar.WEEK_OF_YEAR, -1);
//        val strtTime = cal.timeInMillis;
        val startCal = GregorianCalendar(
                2019,
                1,
                1,
                0,
                0)

        val endCal = GregorianCalendar(
                2019,
                3,
                7,
                23,
                59)

//        java.text.DateFormat dateFormat = getDateInstance();
        val dateFormat = DateFormat.getInstance();
        Log.i(TAG, "Range Start: " + dateFormat.format(startCal.timeInMillis));
        Log.i(TAG, "Range End: " + dateFormat.format(endCal.timeInMillis));

        val readRequest = DataReadRequest.Builder()
                // The data request can specify multiple data types to return, effectively
                // combining multiple data queries into one call.
                // In this example, it's very unlikely that the request is for several hundred
                // datapoints each consisting of a few steps and a timestamp.  The more likely
                // scenario is wanting to see how many steps were walked per day, for 7 days.
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                // bucketByTime allows for a time span, whereas bucketBySession would allow
                // bucketing by "sessions", which would need to be defined in code.
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startCal.timeInMillis, endCal.timeInMillis, TimeUnit.MILLISECONDS)
                .build()
        Log.d(TAG,"Response")
        val response = Fitness.getHistoryClient(activity, gsa).readData(readRequest)
        
        val dayString = dateFormat.format(Date(startCal.timeInMillis))

        Thread {
            try {
                val readDataResult = Tasks.await<DataReadResponse>(response)
                Log.d(TAG, "buckets count: ${readDataResult.buckets.size}")

                if (!readDataResult.buckets.isEmpty()) {
                    val dp = readDataResult.buckets[0].dataSets[0].dataPoints[0]
                    val count = dp.getValue(aggregatedDataType.fields[0])

                    Log.d(TAG, "returning $count steps for $dayString")
                    val map = HashMap<String, Double>()
                    map["value"] = count.asInt().toDouble()

                    result.success(map)
                } else {
                    result.error("No data", "No data found for $dayString", null)
                }
            } catch (e: Throwable) {
                result.error("failed", e.message, null)
            }

        }.start()
//        val response = Fitness.getHistoryClient(activity, gsa).readData(readRequest)
//        response.addOnCompleteListener(object : OnCompleteListener<DataReadResponse>{
//            override fun onComplete(task:Task<DataReadResponse>) {
//                val dataSets = task.getResult().getDataSets()
//                Log.i(TAG, "Data returned for Data type: " + dataSets.size);
////                Log.d(TAG, "buckets count: ${dataSets.buckets.size}")
//            }
//        })
//        response.addOnFailureListener(object : addOnFailureListener<DataReadResponse>{
//            override fun onComplete(task:Task<DataReadResponse>) {
//                val dataSets = task.getResult().getDataSets()
//                Log.i(TAG, "Data returned for Data type: " + dataSets.size);
////                Log.d(TAG, "buckets count: ${dataSets.buckets.size}")
//            }
//        })
//        response.addOnSuccessListener((task) -> {
//            val dataSets = response.getResult().getDataSets()
//            Log.d(TAG, "buckets count: ${dataSets.buckets.size}")
//        })
//        response
//        Thread{
//            val readDataResult = Tasks.await<DataReadResponse>(response)
//            val dataSets = response.getResult().getDataSets()
//            Log.d(TAG, "buckets count: ${readDataResult.buckets.size}")
//
//            Log.d(TAG, dataSets.toString())
//        }.start()

//        dumpDataSet(dataSets)


    }



    private fun getFitnessOptions() = FitnessOptions.builder()
            .addDataType(dataType, FitnessOptions.ACCESS_READ)
            .build()
}
