LISTEN and NOTIFY
PostgreSQL provides a simple transactional message queue that can be used to notify a connection that something interesting has happened.
Such notifications can be tied to database triggers, which provides a way to notify clients that data has changed.
Which is cool.

doobie provides ConnectionIO constructors for SQL LISTEN, UNLISTEN, and NOTIFY in the doobie.postgres.hi.connection module.
New notifications are retrieved (synchronously, sadly, that’s all the driver provides) via pgGetNotifications.
Note that all of the “listening” operations apply to the current connection, which must therefore be long-running
and typically off to the side from normal transactional operations. Further note that you must setAutoCommit(false)
on this connection or commit between each call in order to retrieve messages. The examples project includes a
program that demonstrates how to present a channel as a Stream[IO, PGNotification].

https://jdbc.postgresql.org/documentation/81/listennotify.html

https://stackoverflow.com/questions/21632243/how-do-i-get-asynchronous-event-driven-listen-notify-support-in-java-using-a-p

???
http://impossibl.github.io/pgjdbc-ng/


create table listener_notify(
id numeric constraint pk_listener_notify primary key,
name text
);

insert into listener_notify values(1,'row-1');
insert into listener_notify values(2,'row-2');
insert into listener_notify values(3,'row-3');

--test procedure for data
CREATE OR REPLACE FUNCTION prm_salary.pkg_web_litener_notify(INOUT refcur refcursor, p_user_id numeric DEFAULT NULL::numeric)
 RETURNS refcursor
 LANGUAGE plpgsql
AS $function$
declare
  debug$n numeric;
  errm$c text := '-> prm_salary.pkg_web_litener_notify()';
begin
  debug$n := 1;
  open refcur for
  select *
  from  listener_notify;
exception
  when others then
    raise notice '% sqlerrm %;',errm$c, sqlerrm;
end
$function$
;

-- function to send notify
CREATE OR REPLACE FUNCTION notify_change() RETURNS TRIGGER AS $$
    BEGIN
        SELECT pg_notify('change', TG_TABLE_NAME);
        RETURN NEW;
    END;
$$ LANGUAGE plpgsql;

-- create trigger for each tables
CREATE TRIGGER trg_ch_listener_notify
    AFTER INSERT OR UPDATE OR DELETE ON listener_notify
    FOR EACH ROW EXECUTE PROCEDURE notify_change();
