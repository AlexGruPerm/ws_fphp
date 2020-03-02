package dbconn

import java.sql.Connection

import org.postgresql.PGConnection

case class pgSess(sess : Connection, pid : Int)

case class pgSessListen(conn: Connection, sess : PGConnection, pid : Int)

