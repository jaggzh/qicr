package com.hanhuy.android.irc

import com.hanhuy.android.common._

import java.text.SimpleDateFormat
import java.util.Date

import android.content.Context
import android.support.v4.graphics.drawable.DrawableCompat
import android.view.{Gravity, ViewGroup, View}
import android.widget._
import com.hanhuy.android.common.UiBus
import com.hanhuy.android.irc.model.{BusEvent, RingBuffer}

import iota._

/**
  * @author pfnguyen
  */
object NotificationCenter extends TrayAdapter[NotificationMessage] {
  val NNil: List[NotificationMessage] = Nil
  private[this] val notifications = RingBuffer[NotificationMessage](64)

  def sortedNotifications = {
    val (is, ns) = notifications.foldRight((NNil, NNil)) {
      case (n, (important, normal)) =>
        if (n.isNew && n.important)
          (n :: important, normal)
        else
          (important, n :: normal)
    }
    ns ++ is
  }

  private[this] var sorted = sortedNotifications
  private[this] var newest = 0l

  override def itemId(position: Int) = sorted(position).hashCode

  override def size = sorted.size

  override def emptyItem = R.string.no_notifications

  type LP = RelativeLayout.LayoutParams

  import ViewGroup.LayoutParams._
  import RelativeLayout._

  case class NotificationHolder(icon: ImageView,
                                arrow: ImageView,
                                channelServer: TextView,
                                timestamp: TextView,
                                text: TextView)

  def notificationLayout(implicit c: Context) = {
    val holder = NotificationHolder(
      new ImageView(c),
      new ImageView(c),
      new TextView(c),
      new TextView(c),
      new TextView(c))
    l[RelativeLayout](
      holder.icon.! >>= id(Id.notif_icon) >>=
        k.scaleType(ImageView.ScaleType.CENTER_INSIDE) >>=
        lpK(24.dp, 24.dp) { p: LP =>
          p.addRule(ALIGN_PARENT_LEFT, 1)
          p.addRule(BELOW, Id.channel_server)
          margins(all = 8.dp)(p)
        },
      holder.arrow.! >>= id(Id.notif_arrow) >>=
        k.imageResource(R.drawable.ic_navigate_next_white_24dp) >>=
        k.scaleType(ImageView.ScaleType.CENTER_INSIDE) >>=
        lpK(24.dp, 24.dp) { p: LP =>
          p.addRule(ALIGN_PARENT_RIGHT, 1)
          p.addRule(BELOW, Id.timestamp)
          margins(all = 8.dp)(p)
        } >>= k.visibility(View.GONE) >>= kestrel { iv =>
        DrawableCompat.setTint(iv.getDrawable.mutate(), resolveAttr(R.attr.qicrNotificationIconTint, _.data))
      },
      holder.channelServer.! >>= id(Id.channel_server) >>=
        k.text("#channel / server") >>= lpK(WRAP_CONTENT, WRAP_CONTENT) { p: LP =>
        p.addRule(ALIGN_PARENT_LEFT, 1)
        p.addRule(RIGHT_OF, Id.notif_icon)
        p.addRule(ALIGN_PARENT_TOP, 1)
        margins(left = 8.dp)(p)
      },
      holder.timestamp.! >>= id(Id.timestamp) >>= k.text("9:09pm") >>= lpK(WRAP_CONTENT, WRAP_CONTENT) { p: LP =>
        p.addRule(ALIGN_PARENT_RIGHT, 1)
        p.addRule(ALIGN_PARENT_TOP, 1)
        margins(right = 8.dp)(p)
      },
      holder.text.! >>= id(Id.text) >>= lpK(WRAP_CONTENT, WRAP_CONTENT) { p: LP =>
        p.addRule(ALIGN_TOP, Id.notif_icon)
        p.alignWithParent = true
        p.addRule(RIGHT_OF, Id.notif_icon)
        p.addRule(LEFT_OF, Id.notif_arrow)
      } >>= k.gravity(Gravity.CENTER_VERTICAL)
    ) >>= padding(top = 12.dp, bottom = 12.dp, right = 8.dp, left = 8.dp) >>= kestrel(_.setTag(Id.holder, holder))
  }

  override def onGetView(position: Int, convertView: View, parent: ViewGroup) = {
    implicit val ctx = parent.getContext
    val view = convertView match {
      case vg: ViewGroup => vg
      case _ => notificationLayout.perform()
    }
    val holder = view.getTag(Id.holder).asInstanceOf[NotificationHolder]
    getItem(position) foreach { n =>
      holder.text.setText(n.message)
      holder.icon.setImageResource(n.icon)
      val sdf = new SimpleDateFormat("h:mma")
      holder.timestamp.setText(sdf.format(n.ts).toLowerCase)
      if (n.isNew && n.important) {
        DrawableCompat.setTint(DrawableCompat.wrap(holder.icon.getDrawable.mutate()), 0xff26a69a)
      } else {
        DrawableCompat.setTint(DrawableCompat.wrap(holder.icon.getDrawable.mutate()), resolveAttr(R.attr.qicrNotificationIconTint, _.data))
      }
      n match {
        case NotifyNotification(ts, server, sender, msg) =>
          holder.channelServer.setText(server)
          holder.arrow.setVisibility(View.GONE)
        case UserMessageNotification(ts, server, sender, msg) =>
          holder.arrow.setVisibility(View.VISIBLE)
          holder.channelServer.setText(server)
        case ChannelMessageNotification(ts, server, chan, sender, msg) =>
          holder.arrow.setVisibility(View.VISIBLE)
          holder.channelServer.setText(s"$chan / $server")
        case ServerNotification(ic, server, msg) =>
          holder.arrow.setVisibility(View.GONE)
          holder.channelServer.setText(server)
      }
    }
    view
  }

  override def getItem(position: Int): Option[NotificationMessage] =
    if (size == 0) None else sorted(position).?

  def hasImportantNotifications = sorted.exists(n => n.isNew && n.important)

  def +=(msg: NotificationMessage) = {
    val msgtime = msg.ts.getTime
    if (msgtime > newest) {
      UiBus.run {
        notifications += msg
        if (msg.important) {
          UiBus.send(BusEvent.NewNotification)
        }
        notifyDataSetChanged()
      }
    }

    newest = math.max(newest, msg match {
      case _: ChannelMessageNotification[_] =>
        msgtime
      case _: UserMessageNotification[_] =>
        msgtime
      case _ => 0
    })
  }

  def markAllRead(): Unit = UiBus.run {
    notifications foreach { _.markRead() }
    notifyDataSetChanged()
  }

  def markRead(channel: String, server: String): Unit = {
    notifications collect {
      case n@ChannelMessageNotification(_,s,c,_,_) =>
        if (server == s && channel == c)
          n.markRead()
      case n@UserMessageNotification(_,s,c,_) =>
        if (server == s && channel == c)
          n.markRead()
    }
    UiBus.run(notifyDataSetChanged())
    if (!hasImportantNotifications)
      UiBus.send(BusEvent.ReadNotification)
  }

  override def notifyDataSetChanged() = {
    sorted = sortedNotifications
    super.notifyDataSetChanged()
  }
}

sealed trait NotificationMessage {
  private[this] var newmessage = true
  def message: CharSequence
  def icon: Int
  val ts: Date
  def isNew = newmessage
  def important: Boolean
  def markRead() = newmessage = false
}

case class ServerNotification(icon: Int,
                              server: String,
                              message: CharSequence)
  extends NotificationMessage {
  val important = false
  val ts = new Date
}

case class NotifyNotification(ts: Date,
                              server: String,
                              sender: String,
                              message: CharSequence) extends NotificationMessage {
  val important = false
  val icon = R.drawable.ic_info_outline_black_24dp
}

case class ChannelMessageNotification[A](ts: Date,
                                         server: String,
                                         channel: String,
                                         nick: String,
                                         message: CharSequence)
extends NotificationMessage {
  def action(adapter: MainPagerAdapter) = adapter.selectTab(channel, server)
  val important = true
  val icon = R.drawable.ic_message_black_24dp
}

case class UserMessageNotification[A](ts: Date,
                                      server: String,
                                      nick: String,
                                      message: CharSequence)
  extends NotificationMessage {
  def action(adapter: MainPagerAdapter) = adapter.selectTab(nick, server)
  val icon = R.drawable.ic_chat_black_24dp
  val important = true
}

abstract class TrayAdapter[A] extends BaseAdapter {
  import iota._
  import ViewGroup.LayoutParams._
  final override def getView(position: Int, convertView: View, parent: ViewGroup) = {
    if (size == 0) {
      implicit val context = parent.getContext
      convertView.?.getOrElse {
        // do nothing with onClick so that onItemClick doesn't execute
        c[AbsListView](w[TextView] >>= k.text(emptyItem) >>= k.gravity(Gravity.CENTER) >>=
          hook0.onClick(IO(())) >>= lp(MATCH_PARENT, 128.dp)).perform()
      }
    } else {
      onGetView(position, convertView, parent)
    }
  }

  final override def getItemViewType(position: Int) = if (size == 0 && position == 0) 1 else 0
  final override def getViewTypeCount = 2
  final override def getCount = math.max(1, size)
  final override def getItemId(position: Int) = if (size == 0) -1 else itemId(position)
  final override def hasStableIds = false

  def itemId(position: Int): Int
  def onGetView(position: Int, convertView: View, parent: ViewGroup): View
  def size: Int
  def emptyItem: Int
}
