package com.hanhuy.android.irc

import java.util.UUID

import android.app.{Activity, AlertDialog, Dialog}
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view._
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView.OnScrollListener
import android.widget._

import android.support.v4.app._

import com.hanhuy.android.irc.model.Channel
import com.hanhuy.android.irc.model.Query
import com.hanhuy.android.irc.model.Server
import com.hanhuy.android.irc.model.MessageAdapter
import com.hanhuy.android.irc.model.BusEvent

import com.hanhuy.android.common._
import com.hanhuy.android.conversions._
import com.hanhuy.android.extensions._

import MainActivity._

import Tweaks._

import scala.util.Try
import iota._

class ServerSetupFragment extends DialogFragment {
  val manager = IrcManager.start()

  // hack to store text
  var idholder = 0x10001000
  import ViewGroup.LayoutParams._

  def header = {
    new TextView(getActivity, null, android.R.attr.listSeparatorTextViewStyle)
  }
  def label = c[TableRow](w[TextView] >>=
    lpK(WRAP_CONTENT, WRAP_CONTENT)(margins(right = 12.dp)))

  def inputTweaks: Kestrel[EditText] = c[TableRow](kestrel { e: EditText =>
    e.setSingleLine(true)
    e.setId(idholder)
    idholder = idholder + 1
  } >=> lp(0, WRAP_CONTENT, 1))

  var layoutInit = false
  lazy val layout = {
    layoutInit = true
    c[ViewGroup](l[ScrollView](
      l[TableLayout](
        IO(header) >>= text("Connection Info"),
        l[TableRow](
          label >>= text("Name"),
          IO(server_name) >>= inputTweaks >>= hint("required") >>= textCapWords
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          label >>= text("Server address"),
          IO(server_host) >>= inputTweaks >>= hint("required") >>= textUri
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          label >>= text("Port"),
          IO(port) >>= inputTweaks >>= hint("Default: 6667") >>= number
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          w[View],
          IO(autoconnect) >>= text("Enable Autoconnect")
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          w[View],
          IO(ssl) >>= text("Enable SSL")
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        IO(header) >>= text("User Info"),
        l[TableRow](
          label>>= text("Nickname"),
          IO(nickname) >>= inputTweaks >>= hint("required")
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          label >>= text("Alt. nick"),
          IO(altnick) >>= inputTweaks >>= hint("Default: <Nickname>_")
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          label >>= text("Real name"),
          IO(realname) >>= inputTweaks >>= hint("required") >>= textCapWords
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          w[View],
          IO(sasl) >>= text("SASL authentication")
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          label >>= text("Username"),
          IO(username) >>= inputTweaks >>= hint("required")
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          label >>= text("Password"),
          IO(password) >>= inputTweaks >>= hint("optional") >>= textPassword
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        IO(header) >>= text("Session Options"),
        l[TableRow](
          label >>= text("Auto join"),
          IO(autojoin) >>= inputTweaks >>= hint("#chan1 key;#chan2")
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT),
        l[TableRow](
          label >>= text("Auto run"),
          IO(autorun) >>= inputTweaks >>= hint("m pfn hi there;")
        ) >>= lp(MATCH_PARENT, WRAP_CONTENT)
      ) >>= lpK(MATCH_PARENT, MATCH_PARENT)(margins(all = 8 dp)) >>=
        kestrel { t => t.setColumnStretchable(1, true) }
    ) >>= lp(MATCH_PARENT, MATCH_PARENT) >>=
      condK(tablet ? kitkatPadding)).perform()
  }

  lazy val server_name = new EditText(getActivity)
  lazy val server_host = new EditText(getActivity)
  lazy val port = new EditText(getActivity)
  lazy val ssl = checkbox
  lazy val autoconnect = checkbox
  lazy val nickname = new EditText(getActivity)
  lazy val altnick = new EditText(getActivity)
  lazy val realname = new EditText(getActivity)
  lazy val username = new EditText(getActivity)
  lazy val password = new EditText(getActivity)
  lazy val autojoin = new EditText(getActivity)
  lazy val autorun = new EditText(getActivity)
  lazy val sasl = checkbox

  val _server: Server = new Server
  def server: Server = {
    val s = _server
    s.name        = server_name.getText.toString
    s.hostname    = server_host.getText.toString
    s.port        = Try(port.getText.toString.toInt).toOption getOrElse 6667
    s.ssl         = ssl.isChecked
    s.autoconnect = autoconnect.isChecked
    s.nickname    = nickname.getText.toString
    s.altnick     = altnick.getText.toString
    s.realname    = realname.getText.toString
    s.username    = username.getText.toString
    s.password    = password.getText.toString
    s.autojoin    = autojoin.getText.toString
    s.autorun     = autorun.getText.toString
    s.sasl        = sasl.isChecked
    _server
  }
  def server_=(s: Server) = {
    _server.copy(s)
    if (layoutInit && s != null) {
      server_name.setText(s.name)
      server_host.setText(s.hostname)
      port.setText("" + s.port)
      ssl.setChecked(s.ssl)
      sasl.setChecked(s.sasl)
      autoconnect.setChecked(s.autoconnect)
      nickname.setText(s.nickname)
      altnick.setText(s.altnick)
      realname.setText(s.realname)
      username.setText(s.username)
      password.setText(s.password)
      autojoin.setText(s.autojoin)
      autorun.setText(s.autorun)
    }
  }

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setHasOptionsMenu(true)
    //setRetainInstance(true)
  }

  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
    inflater.inflate(R.menu.server_setup_menu, menu)

  override def onOptionsItemSelected(item: MenuItem) = {
    val R_id_cancel_server = R.id.cancel_server
    val R_id_save_server = R.id.save_server
    item.getItemId match {
      case R_id_save_server =>
        val activity = getActivity
        val fm = activity.getSupportFragmentManager
        val s = server
        if (s.valid) {
          if (s.id == -1)
            manager.addServer(s)
          else
            manager.updateServer(s)
          fm.popBackStack()
        } else {
          Toast.makeText(getActivity, R.string.server_incomplete,
            Toast.LENGTH_SHORT).show()
        }
        true
      case R_id_cancel_server =>
        val manager = getActivity.getSupportFragmentManager
        manager.popBackStack()
        true
      case _ => false
    }
  }

  override def onCreateView(inflater: LayoutInflater,
      container: ViewGroup, bundle: Bundle) : View = {
    // otherwise an AndroidRuntimeException occurs
    if (dialogShown) return super.onCreateView(inflater, container, bundle)

    val l = layout
    if (bundle == null)
      server = _server
    l
  }

  var dialogShown = false
  override def onCreateDialog(bundle: Bundle): Dialog = {
    dialogShown = true
    val activity = getActivity
    //val m = activity.settings.get(Settings.DAYNIGHT_MODE)
    //import android.view.ContextThemeWrapper
    //val d = new AlertDialog.Builder(new ContextThemeWrapper(activity,
    //    if (m) R.style.AppTheme_Light else R.style.AppTheme_Dark))
    val d: AlertDialog = new AlertDialog.Builder(activity)
      .setTitle(R.string.server_details)
      .setPositiveButton(R.string.save_server, null)
      .setNegativeButton(R.string.cancel_server, null)
      .setView(layout)
      .create()
    if (bundle == null)
      server = _server
    // block dismiss on positive button click
    d.onShow0 {
      val b = d.getButton(DialogInterface.BUTTON_POSITIVE)
      b.onClick0 {
        val s = server
        if (s != null && s.valid) {
          if (s.id == -1)
            manager.addServer(s)
          else
            manager.updateServer(s)
          d.dismiss()
        } else {
          Toast.makeText(getActivity,
            R.string.server_incomplete,
            Toast.LENGTH_SHORT).show()
        }
      }
    }
    d.getWindow.setSoftInputMode(
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    d
  }
}

abstract class MessagesFragment
extends Fragment with EventBus.RefOwner {

  def adapter: Option[MessageAdapter]

  var lookupId: String = ""

  private[this] var listView: ListView = _

  def tag: String

  import ViewGroup.LayoutParams._
  def layout = c[FrameLayout](w[ListView] >>= id(android.R.id.list) >>= lp(MATCH_PARENT, MATCH_PARENT) >>=
    kitkatPadding(getActivity.tabs.getVisibility == View.GONE) >>=
    kestrel { l =>
      l.setDrawSelectorOnTop(true)
      l.setDivider(new ColorDrawable(Color.BLACK))
      l.setDividerHeight(0)
      l.setChoiceMode(AbsListView.CHOICE_MODE_NONE)
      l.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL)
      l.setSelector(R.drawable.message_selector)
      if (v(19)) l.setClipToPadding(false)
      l.setDrawSelectorOnTop(true)
      if (!getActivity.isFinishing)
        l.setAdapter(adapter.get)
      l.setOnScrollListener(new OnScrollListener {
        import OnScrollListener._
        override def onScrollStateChanged(v: AbsListView, s: Int) {
          if (s == SCROLL_STATE_TOUCH_SCROLL || s == SCROLL_STATE_FLING) {
            val imm = getActivity.systemService[InputMethodManager]
            val focused = Option(getActivity.getCurrentFocus)
            focused foreach { f =>
              imm.hideSoftInputFromWindow(f.getWindowToken, 0)
            }
          }
        }

        override def onScroll(p1: AbsListView, p2: Int, p3: Int, p4: Int) {}
      })
    })

  val manager = IrcManager.start()

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    if (bundle != null)
      lookupId = bundle.getString("channel.key")

    if (adapter.isEmpty) {
      manager.queueCreateActivity(0)
      if (!getActivity.isFinishing)
        getActivity.finish()
    }
  }


  override def onSaveInstanceState(outState: Bundle) = {
    lookupId = UUID.randomUUID.toString
    outState.putString("channel.key", lookupId)
    super.onSaveInstanceState(outState)
  }

  override def onResume() {
    super.onResume()
    adapter foreach (_.context = getActivity)
    scrollToEnd()
  }

  def scrollToEnd() {
    if (listView != null)
      for {
        a <- adapter
      } listView.setSelection(a.getCount - 1)
  }

  override def onCreateView(i: LayoutInflater, c: ViewGroup, b: Bundle) = {
    adapter foreach (_.context = getActivity)
    val v = layout.perform()
    listView = v
    def inputHeight = for {
      a <- MainActivity.instance
      h <- a.inputHeight
    } yield h

    inputHeight.fold {
      v.getViewTreeObserver.addOnPreDrawListener(new OnPreDrawListener {
        override def onPreDraw() = {
          inputHeight exists { h =>
            v.getViewTreeObserver.removeOnPreDrawListener(this)
            val p = v.getPaddingBottom + h
            v.setPadding(
              v.getPaddingLeft, v.getPaddingTop, v.getPaddingRight, p)
            true
          }
        }
      })
    }{ h =>
      val p = v.getPaddingBottom + h
      v.setPadding(v.getPaddingLeft, v.getPaddingTop, v.getPaddingRight, p)
    }
    v
  }

}

class ChannelFragment(_channel: Option[Channel])
  extends MessagesFragment with EventBus.RefOwner {

  def this() = this(None)

  lazy val channel = _channel orElse {
    manager.getChannel[Channel](lookupId)
  }

  override lazy val adapter = channel map (_.messages)

  lazy val tag = getFragmentTag(channel)
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    setHasOptionsMenu(true)
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    channel foreach { c =>
      manager.saveChannel(lookupId, c)
    }
  }

  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) = {
    inflater.inflate(R.menu.channel_menu, menu)
    if (!Settings.get(Settings.IRC_LOGGING)) {
      val item = menu.findItem(R.id.channel_log)
      item.setVisible(false)
    }
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    if (R.id.nicklist == item.getItemId) {
      MainActivity.instance foreach { _.toggleNickList() }
    }
    if (R.id.channel_log == item.getItemId) {
      startActivity(MessageLogActivity.createIntent(channel.get))
      getActivity.overridePendingTransition(
        R.anim.slide_in_left, R.anim.slide_out_right)
      return true
    }
    if (R.id.channel_close == item.getItemId) {
      val activity = getActivity
      val prompt = Settings.get(Settings.CLOSE_TAB_PROMPT)

      channel foreach { c =>
        def removeChannel() {
          if (channel != null && c.state == Channel.State.JOINED) {
            manager.channels.get(c) foreach {
              _.part()
            }
          }
          manager.remove(c)
          activity.adapter.removeTab(activity.adapter.getItemPosition(this))
        }
        if (c.state == Channel.State.JOINED && prompt) {
          val builder = new AlertDialog.Builder(activity)
          builder.setTitle(R.string.channel_close_confirm_title)
          builder.setMessage(getString(R.string.channel_close_confirm))
          builder.setPositiveButton(R.string.yes, () => {
            removeChannel()
            c.state = Channel.State.PARTED
          })
          builder.setNegativeButton(R.string.no, null)
          builder.create().show()
        } else {
          removeChannel()
        }
      }
      return true
    }
    false
  }
}

class QueryFragment(_query: Option[Query]) extends MessagesFragment {
  def this() = this(None)
  lazy val query = _query orElse {
    manager.getChannel[Query](lookupId)
  }
  override lazy val adapter = query map (_.messages)
  lazy val tag = getFragmentTag(query)

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setHasOptionsMenu(true)
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    query foreach { q =>
      manager.saveChannel(lookupId, q)
    }
  }
  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) = {
    inflater.inflate(R.menu.query_menu, menu)
    if (!Settings.get(Settings.IRC_LOGGING)) {
      val item = menu.findItem(R.id.channel_log)
      item.setVisible(false)
    }
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    if (R.id.query_close == item.getItemId) {
      val activity = getActivity
      val prompt = Settings.get(Settings.CLOSE_TAB_PROMPT)
      def removeQuery() {
        manager.remove(query.get)
        activity.adapter.removeTab(activity.adapter.getItemPosition(this))
      }
      if (prompt) {
        val builder = new AlertDialog.Builder(activity)
        builder.setTitle(R.string.query_close_confirm_title)
        builder.setMessage(getString(R.string.query_close_confirm))
        builder.setPositiveButton(R.string.yes, removeQuery _)
        builder.setNegativeButton(R.string.no, null)
        builder.create().show()
        return true
      } else
        removeQuery()
      return true
    } else if (R.id.channel_log == item.getItemId) {
      startActivity(MessageLogActivity.createIntent(query.get))
      getActivity.overridePendingTransition(
        R.anim.slide_in_left, R.anim.slide_out_right)
      return true
    }
    false
  }

}

class ServerMessagesFragment(_server: Option[Server]) extends MessagesFragment {
  def this() = this(None)
  lazy val server = _server orElse {
    manager.getChannel[Server](lookupId)
  }
  override lazy val adapter = server map (_.messages)
  lazy val tag = getFragmentTag(server)
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setHasOptionsMenu(true)
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    server foreach { s =>
      manager.saveChannel(lookupId, s)
    }
  }
  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    inflater.inflate(R.menu.server_messages_menu, menu)
    server.foreach { s =>
      val connected = s.state match {
        case Server.State.INITIAL => false
        case Server.State.DISCONNECTED => false
        case _ => true
      }

      menu.findItem(R.id.server_connect).setVisible(!connected)
      menu.findItem(R.id.server_disconnect).setVisible(connected)
    }
  }

  override def onOptionsItemSelected(item: MenuItem) : Boolean = {
    if (R.id.server_close == item.getItemId) {
      getActivity.adapter.removeTab(getActivity.adapter.getItemPosition(this))
      true
    } else {
      // need to look up server in case it was edited
      val r = getActivity.servers.onServerMenuItemClicked(item, for {
        s <- server
        n <- Config.servers.find(_.id == s.id)
      } yield n)
      if (r) HoneycombSupport.invalidateActionBar()
      r
    }
  }
}

class ServersFragment extends ListFragment
with EventBus.RefOwner {
  val manager = IrcManager.start()
  var adapter: ServersAdapter = _
  var _server: Option[Server] = None // currently selected server
  var serverMessagesFragmentShowing: Option[String] = None

  import ViewGroup.LayoutParams._
  lazy val layout = l[LinearLayout](
    l[LinearLayout](
      w[TextView] >>= text(R.string.server_none) >>= lpK(MATCH_PARENT, WRAP_CONTENT)(
        margins(all = getResources.getDimensionPixelSize(R.dimen.standard_margin))),
      w[Button] >>= id(R.id.add_server) >>= text(R.string.add_server) >>=
        hook0.onClick(IO {
          getActivity.servers.addServerSetupFragment()
        }) >>= lpK(MATCH_PARENT, WRAP_CONTENT)(margins(
        all = getResources.getDimensionPixelSize(R.dimen.standard_margin)))
    ) >>= id(android.R.id.empty) >>= vertical >>= lp(0, MATCH_PARENT, 1) >>= kitkatPadding(getActivity.tabs.getVisibility == View.GONE),
    w[ListView] >>= id(android.R.id.list) >>= kestrel { l =>
      l.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE)
      l.setDrawSelectorOnTop(false)
      if (v(19)) l.setClipToPadding(false)
    } >>= lp(0, MATCH_PARENT, 1) >>= kitkatPadding(getActivity.tabs.getVisibility == View.GONE)
  ) >>= id(Id.servers_container) >>= horizontal

  UiBus += {
    case e: BusEvent.ServerAdded   => addListener(e.server)
    case e: BusEvent.ServerChanged => changeListener(e.server)
    case e: BusEvent.ServerRemoved => removeListener(e.server)
  }

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setHasOptionsMenu(true)
    setRetainInstance(true) // retain for serverMessagesFragmentShowing
  }

  override def onActivityCreated(bundle: Bundle) {
    super.onActivityCreated(bundle)
    // retain instance results in the list items having the wrong theme?
    // so recreate the adapter here
    adapter = new ServersAdapter(getActivity)
    setListAdapter(adapter)
    if (manager != null) {
      adapter.notifyDataSetChanged()
    }
  }

  override def onResume() {
    super.onResume()
    HoneycombSupport.menuItemListener = Option(onServerMenuItemClicked)
  }

  override def onCreateView(inflater: LayoutInflater,
      container: ViewGroup, bundle: Bundle) = {
    val v = layout.perform()
    v
  }

  override def onListItemClick(list: ListView, v: View, pos: Int, id: Long) {
    adapter.notifyDataSetChanged()
    val activity = getActivity
    val manager = activity.getSupportFragmentManager
    manager.popBackStack(SERVER_SETUP_STACK,
      FragmentManager.POP_BACK_STACK_INCLUSIVE)
    val server = adapter.getItem(pos)
    HoneycombSupport.invalidateActionBar()
    HoneycombSupport.startActionMode(server)

    if (server.state == Server.State.CONNECTED) {
      activity.input.setVisibility(View.VISIBLE)
    }
//    if (activity.isLargeScreen)
//      addServerMessagesFragment(server)

    _server = Some(server)
  }

  def clearServerMessagesFragment(mgr: FragmentManager,
      tx: FragmentTransaction = null) {
    var mytx: FragmentTransaction = null
    if (tx == null) // need mytx to commit if set
      mytx = mgr.beginTransaction()

    serverMessagesFragmentShowing foreach { name =>
      val f = mgr.findFragmentByTag(name)
      if (f != null) {
        if (mytx != null)
          mytx.hide(f)
        else if (tx != null)
          tx.hide(f)
      }
    }

    if (mytx != null)
      mytx.commit()
  }

  def addServerMessagesFragment(server: Server) {
    val mgr = getActivity.getSupportFragmentManager
    val name = SERVER_MESSAGES_FRAGMENT_PREFIX + server.name
    var fragment = mgr.findFragmentByTag(name).asInstanceOf[MessagesFragment]

    mgr.popBackStack(SERVER_SETUP_STACK,
      FragmentManager.POP_BACK_STACK_INCLUSIVE)
    val tx = mgr.beginTransaction()
    clearServerMessagesFragment(mgr, tx)

    serverMessagesFragmentShowing = Some(name)
    if (fragment == null) {
      fragment = new ServerMessagesFragment(Some(server))
      tx.add(Id.servers_container, fragment, name)
    } else {
      tx.remove(fragment)
      fragment = new ServerMessagesFragment(Some(server))
      tx.add(Id.servers_container, fragment, name)
      if (fragment.isDetached)
        tx.attach(fragment)
      // fragment is sometimes visible without being shown?
      // showing again shouldn't hurt?
      //if (fragment.isVisible()) return
      tx.show(fragment)
    }

    tx.commit()
  }

  def addServerSetupFragment(_s: Option[Server] = None) {
    val activity = getActivity
    val mgr = activity.getSupportFragmentManager
    var fragment: ServerSetupFragment = null
    fragment = mgr.findFragmentByTag(SERVER_SETUP_FRAGMENT)
      .asInstanceOf[ServerSetupFragment]
    if (fragment == null)
      fragment = new ServerSetupFragment
    if (fragment.isVisible) return

    val server = _s getOrElse {
      val listview = getListView
      val checked = listview.getCheckedItemPosition
      if (AdapterView.INVALID_POSITION != checked)
        listview.setItemChecked(checked, false)
      new Server
    }
    val tx = mgr.beginTransaction()
    clearServerMessagesFragment(mgr, tx)

//    if (activity.isLargeScreen) {
//      tx.add(R.id.servers_container, fragment, SERVER_SETUP_FRAGMENT)
//      tx.addToBackStack(SERVER_SETUP_STACK)
//      tx.commit() // can't commit a show
//    } else {
      fragment.setStyle(DialogFragment.STYLE_NO_TITLE,
        resolveAttr(R.attr.qicrCurrentTheme, _.resourceId))
      fragment.show(tx, SERVER_SETUP_FRAGMENT)
//    }

    fragment.server = server
    getActivity.input.setVisibility(View.INVISIBLE)
    UiBus.post { HoneycombSupport.invalidateActionBar() }
  }

  def changeListener(server: Server) {
    _server foreach { s =>
      val a = getActivity
      if (s == server && s.state == Server.State.CONNECTED && a != null)
        a.input.setVisibility(View.VISIBLE)
    }
    if (adapter != null)
      adapter.notifyDataSetChanged()
  }

  def removeListener(server: Server) {
    if (adapter != null) {
      adapter.notifyDataSetChanged()
    }
  }

  def addListener(server: Server) {
    if (adapter == null) return
    adapter.notifyDataSetChanged()
  }

  def onServerMenuItemClicked(item: MenuItem, server: Option[Server]):
      Boolean = {
    val R_id_server_delete = R.id.server_delete
    val R_id_server_messages = R.id.server_messages
    val R_id_server_options = R.id.server_options
    val R_id_server_connect = R.id.server_connect
    val R_id_server_disconnect = R.id.server_disconnect
    item.getItemId match {
      case R_id_server_delete =>
        server match {
        case Some(s) =>
          val builder = new AlertDialog.Builder(getActivity)
          val mgr = getActivity.getSupportFragmentManager
          clearServerMessagesFragment(mgr)
          builder.setTitle(R.string.server_confirm_delete)
          builder.setMessage(getActivity.getString(
            R.string.server_confirm_delete_message,
            s.name))
          builder.setPositiveButton(R.string.yes,
            () => {
              manager.deleteServer(s)
              ()
            })
          builder.setNegativeButton(R.string.no, null)
          builder.create().show()
        case None =>
          Toast.makeText(getActivity,
            R.string.server_not_selected, Toast.LENGTH_SHORT).show()
      }
        true
      case R_id_server_connect =>
        server.fold{
          Toast.makeText(getActivity, R.string.server_not_selected,
            Toast.LENGTH_SHORT).show()
        }(manager.connect)
        true
      case R_id_server_disconnect =>
        server.fold {
          Toast.makeText(getActivity, R.string.server_not_selected,
            Toast.LENGTH_SHORT).show()
        }(manager.disconnect(_))
        true
      case R_id_server_options =>
        addServerSetupFragment(server)
        true
      case R_id_server_messages =>
        server.fold {
          Toast.makeText(getActivity, R.string.server_not_selected,
            Toast.LENGTH_SHORT).show()
        }(getActivity.adapter.addServer)
        true
      case _ => false
    }
  }

  override def onContextItemSelected(item: MenuItem) =
    onServerMenuItemClicked(item, _server)

  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
    inflater.inflate(R.menu.servers_menu, menu)

  override def onOptionsItemSelected(item: MenuItem) : Boolean = {
    if (R.id.add_server == item.getItemId) {
      getActivity.servers.addServerSetupFragment()
      return true
    }
    onServerMenuItemClicked(item, _server)
  }

  override def onPrepareOptionsMenu(menu: Menu) {
    val activity = getActivity
    if (activity != null) {
      val m = activity.getSupportFragmentManager

      var page = 0
      if (activity.adapter != null)
        page = activity.adapter.page

      val found = page == 0 && ((0 until m.getBackStackEntryCount) exists {
        i => m.getBackStackEntryAt(i).getName == SERVER_SETUP_STACK
      })

      menu.findItem(R.id.add_server).setVisible(!found)
    }
  }
  class ServersAdapter(override val context: Activity) extends BaseAdapter with HasContext {
    val manager = IrcManager.start()

    override def getCount = manager.getServers.size

    override def getItemId(p1: Int) = p1

    override def getItem(x: Int) = manager.getServers(x)

    def progressBar = IO(new ProgressBar(
      context, null, R.attr.qicrProgressSpinnerStyle))
//    val layout = w[LinearLayout]
    val layout = c[AbsListView](l[LinearLayout](
      l[FrameLayout](
        progressBar >>= id(Id.server_item_progress) >>=
          lp(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER) >>=
          padding(left = 6 dp, right = 6 dp) >>= kestrel { p: ProgressBar =>
          p.setIndeterminate(true) } >>= gone,
        w[ImageView] >>= lp(64 dp, 64 dp, Gravity.CENTER) >>=
          imageResource(android.R.drawable.presence_offline) >>=
          imageScale(ImageView.ScaleType.CENTER_INSIDE) >>= padding(left = 6 dp, right = 6 dp) >>=
          id(Id.server_item_status)
      ) >>= kestrel { v: FrameLayout => v.setMeasureAllChildren(true) },
      w[TextView] >>= lp(0, 64 dp, 1) >>=
        padding(left = 6 dp, right = 6 dp) >>= kestrel { tv =>
          tv.setGravity(Gravity.CENTER_VERTICAL)
          tv.setTextAppearance(context, android.R.style.TextAppearance_Large)
        } >>= id(Id.server_item_text),
      IO(checkedText) >>= lp(96 dp, 64 dp) >>= padding(right = 6 dp) >>=
        kestrel { tv =>
          tv.setGravity(Gravity.CENTER_VERTICAL)
          tv.setTextAppearance(context, android.R.style.TextAppearance_Small)

          tv.setCheckMarkDrawable(
            resolveAttr(android.R.attr.listChoiceIndicatorSingle, _.resourceId))
        } >>= id(Id.server_checked_text)
    ) >>= kestrel { _.setGravity(Gravity.CENTER_VERTICAL) } >>=
      lp(MATCH_PARENT, WRAP_CONTENT))

    override def getView(pos: Int, convertView: View, parent: ViewGroup) = {
      import Server.State._
      val server = getItem(pos)
      val list = parent.asInstanceOf[ListView]

      val v = if (convertView != null)
        convertView.asInstanceOf[ViewGroup]
      else
        layout.perform()

      val checked = list.getCheckedItemPosition
      val img = v.findView(Id.server_item_status)

      (IO(v.findView(Id.server_item_text)) >>= text(server.name)).perform()
      (IO(v.findView(Id.server_item_progress)) >>= condK(
        (server.state == Server.State.CONNECTING) ? visible
        | gone)).perform()

      (IO(img) >>= imageResource(server.state match {
        case INITIAL      => android.R.drawable.presence_offline
        case DISCONNECTED => android.R.drawable.presence_busy
        case CONNECTED    => android.R.drawable.presence_online
        case CONNECTING   => android.R.drawable.presence_away
      }) >>= condK((server.state != Server.State.CONNECTING) ? visible | gone)).perform()

      val t = IO(v.findView(Id.server_checked_text) : CheckedTextView)
      (t >>= kestrel { tv =>
        tv.setChecked(pos == checked)
        val lag = if (server.state == CONNECTED) {
          val l = server.currentPing flatMap { p =>
            if (server.currentLag == 0) None
            else Some((System.currentTimeMillis - p).toInt)
          } getOrElse server.currentLag
          Server.intervalString(l)
        } else ""
        tv.setText(lag)
      }).perform()
      v
    }
  }
}


