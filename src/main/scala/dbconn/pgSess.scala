package dbconn

import java.sql.Connection

case class pgSess(sess : Connection, pid : Int)