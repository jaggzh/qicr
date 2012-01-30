package com.hanhuy.android.irc

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.res.Configuration
import android.os.AsyncTask
import android.os.Build
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.TextView
import android.widget.CheckBox
import android.content.DialogInterface

object AndroidConversions {
    val icsAndNewer =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
    val honeycombAndNewer =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
    val gingerbreadAndNewer =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD

    if (gingerbreadAndNewer) GingerbreadSupport.init()
    implicit def toBroadcastReceiver(f: (Context, Intent) => Unit) =
            new BroadcastReceiver() {
        def onReceive(c: Context, i: Intent) = f(c, i)
    }

    implicit def toViewOnClickListener1(f: () => Unit) =
            new View.OnClickListener() { def onClick(v: View) = f() }
    implicit def toViewOnClickListener(f: View => Unit) =
            new View.OnClickListener() { def onClick(v: View) = f(v) }

    implicit def toDialogInterfaceOnClickListener(
            f: (DialogInterface, Int) => Unit) = {
        new DialogInterface.OnClickListener() {
            def onClick(d: DialogInterface, id: Int) = f(d, id)
        }
    }
    implicit def toDialogInterfaceOnClickListener1(f: () => Unit) = {
        new DialogInterface.OnClickListener() {
            def onClick(d: DialogInterface, id: Int) = f()
        }
    }

    implicit def toDialogInterfaceOnShowListener(f: () => Unit) = {
        new DialogInterface.OnShowListener() {
            def onShow(d: DialogInterface) = f()
        }
    }

    implicit def toAdapterViewOnItemClickListener(
            f: (AdapterView[_], View, Int, Long) => Unit) =
                    new AdapterView.OnItemClickListener() {
                def onItemClick(
                        av: AdapterView[_], v: View, pos: Int, id: Long) =
                            f(av, v, pos, id)
    }

    implicit def toViewOnKeyListener(f: (View, Int, KeyEvent) => Boolean) =
                    new View.OnKeyListener() {
                def onKey(v: View, key: Int, e: KeyEvent) = f(v, key, e)
    }
    implicit def toViewOnTouchListener(f: (View, MotionEvent) => Boolean) =
                    new View.OnTouchListener() {
                def onTouch(v: View, e: MotionEvent) = f(v, e)
    }

    implicit def toTextViewOnEditorAction(f: (View, Int, KeyEvent) => Boolean) =
            new TextView.OnEditorActionListener() {
        def onEditorAction(v: TextView, action: Int, e: KeyEvent) =
            f(v, action, e)
    }

    implicit def toRunnable(f: () => Unit) = new Runnable() { def run() = f() }

    def async(task: AsyncTask[_,_,_]) {
        if (honeycombAndNewer)
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        else
            task.execute()
    }
    def async(r: Runnable) = _threadpool.execute(r)
    // ok, param: => T can only be used if called directly, no implicits
    def async(f: => Unit): Unit = async(byNameToRunnable(f))
    private def byNameToRunnable(f: => Unit) = new Runnable() { def run() = f }

    implicit def toUncaughtExceptionHandler(f: (Thread, Throwable) => Unit) =
            new Thread.UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable) = f(t, e)
    }

    implicit def toString(c: CharSequence) =
            if (c == null) null else c.toString()
    implicit def toString(t: TextView): String = t.getText()
    implicit def toInt(t: TextView) = {
        val s: String = t.getText()
        if (s == null || s == "") -1
        else Integer.parseInt(s)
    }
    implicit def toBoolean(c: CheckBox) = c.isChecked()

    implicit def toRichView(v: View) = new RichView(v)
    implicit def toRichContext(c: Context) = new RichContext(c)
    implicit def toRichActivity(a: Activity) = new RichActivity(a)

    lazy val _threadpool = {
        if (honeycombAndNewer) AsyncTask.THREAD_POOL_EXECUTOR
        else { // basically how THREAD_POOL_EXECUTOR is defined in api11+
            import java.util.concurrent._
            import java.util.concurrent.atomic._
            // initial, max, keep-alive time
            new ThreadPoolExecutor(5, 128, 1, TimeUnit.SECONDS,
                    new LinkedBlockingQueue[Runnable](10),
                    new ThreadFactory() {
                        val count = new AtomicInteger(1)
                        override def newThread(r: Runnable) =
                                new Thread(r,
                                        "AsyncPool #" + count.getAndIncrement)
                    }
            )
        }
    }
    def isMainThread = Looper.getMainLooper.getThread == currentThread
}

case class SystemService[T](name: String)
object SystemService {
    implicit val layoutInflaterService =
            SystemService[LayoutInflater](Context.LAYOUT_INFLATER_SERVICE)
    implicit val notificationService =
            SystemService[NotificationManager](Context.NOTIFICATION_SERVICE)
}
class RichContext(context: Context) {
    def systemService[T](implicit s: SystemService[T]): T =
            context.getSystemService(s.name).asInstanceOf[T]
}
class RichView(view: View) {
    import AndroidConversions._
    def findView[T](id: Int): T = view.findViewById(id).asInstanceOf[T]
    def onClick_= (f: => Unit) = view.setOnClickListener { () => f }
}
class RichActivity(activity: Activity) extends RichContext(activity) {
    import Configuration._
    lazy val config = activity.getResources.getConfiguration

    def findView[T](id: Int): T = activity.findViewById(id).asInstanceOf[T]

    lazy val isLargeScreen =
            (config.screenLayout & SCREENLAYOUT_SIZE_LARGE) ==
                    SCREENLAYOUT_SIZE_LARGE
    lazy val isXLargeScreen =
            (config.screenLayout & SCREENLAYOUT_SIZE_XLARGE) ==
                    SCREENLAYOUT_SIZE_XLARGE
}
