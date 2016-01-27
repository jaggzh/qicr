package com.hanhuy.android.irc

import android.app.Activity
import android.content.Intent
import android.support.v4.app.Fragment
import com.hanhuy.android.common.Logcat

import scala.concurrent.{Future, Promise}

/**
  * @author pfnguyen
  */

trait BaseResultManager {
  case object ActivityResultCancel extends Exception
  case class ActivityResultProcessor[A,B](startActivityForResult: (Intent, Int) => A, onActivityResult: (Int, Int, Intent) => B)

  def activityFragmentWrapper: ActivityResultProcessor[_,_]
  private[this] var _requestCode = 0
  final def requestCode = {
    _requestCode = _requestCode + 1
    _requestCode % 0xff
  }
  private[this] val arlog = Logcat("ActivityResultManager")
  private[this] var results = Map.empty[Int,Promise[Intent]]
  protected def processActivityResult(request: Int,
                                      resultCode: Int,
                                      data: Intent): Unit = {
    if (results.contains(request)) {
      if (resultCode != Activity.RESULT_OK) {
        results(request).failure(ActivityResultCancel)
      } else {
        results(request).success(data)
      }
      results -= request
    } else {
      activityFragmentWrapper.onActivityResult(request, resultCode, data)
      arlog.w(s"Request code $request not found: " + data)
    }
  }
  final def requestActivityResult(intent: Intent): Future[Intent] = {
    val req = requestCode
    val p = results.getOrElse(req, Promise[Intent]())
    results += ((req, p))
    activityFragmentWrapper.startActivityForResult(intent, req)
    p.future
  }
}
trait ActivityResultManager extends Activity with BaseResultManager {
  final def activityFragmentWrapper = ActivityResultProcessor(
    (i,r) => startActivityForResult(i, r),
    (req, res, data) => super.onActivityResult(req, res, data))

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = processActivityResult(requestCode, resultCode, data)
}
trait FragmentResultManager extends Fragment with BaseResultManager {
  final def activityFragmentWrapper = ActivityResultProcessor(
    (i,r) => startActivityForResult(i, r),
    (req, res, data) => super.onActivityResult(req, res, data))

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = processActivityResult(requestCode, resultCode, data)
}
