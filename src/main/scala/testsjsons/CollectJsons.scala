package testsjsons

object CollectJsons {

  val reqJsonText_@ =
    """
      |              { "user_session" : "c4ec52189bd51acb95bc2a5082c7c014",
      |                "cont_encoding_gzip_enabled" : 1,
      |                 "thread_pool" : "block",
      |                 "request_timeout_ms": 5000,
      |                 "cache_live_time" : 60000,
      |                "dicts": [
      |                  {
      |                    "name" : "oiv",
      |                    "db" : "dbp",
      |                    "proc" : "prm_salary.pkg_web_cons_rep_grbs_list(refcur => ?, p_user_id => 45224506)",
      |                    "reftables" : ["sl_user_access","sl_d_grbs","sl_f_cons_rep","sl_inst_grbs_link","sl_institution" ]
      |                  },
      |                 {
      |                  "name" : "okfs",
      |                  "db" : "dbp",
      |                  "proc":"prm_salary.pkg_web_cons_rep_okfs_list(refcur => ?)",
      |                  "reftables" : ["sl_d_okfs"]
      |                }
      |              ]
      |             }
      |""".stripMargin

  val reqJsonText_123 =
    """
      |              { "user_session" : "c4ec52189bd51acb95bc2a5082c7c014",
      |                "cont_encoding_gzip_enabled" : 1,
      |                 "thread_pool" : "block",
      |                 "request_timeout_ms": 5000,
      |                 "cache_live_time" : 60000,
      |                "dicts": [
      |                {
      |                  "name" : "okfs_users",
      |                  "db" : "dbp",
      |                  "proc":"prm_salary.pkg_web_cons_rep_okfs_users(refcur => ?)",
      |                  "reftables" : ["prm_salary.sl_d_okfs","prm_admin.t_users"]
      |                }
      |              ]
      |             }
      |""".stripMargin


  val reqJsonText_ =
    """
      |              { "user_session" : "c4ec52189bd51acb95bc2a5082c7c014",
      |                "cont_encoding_gzip_enabled" : 1,
      |                 "thread_pool" : "block",
      |                 "request_timeout_ms": 5000,
      |                 "cache_live_time" : 60000,
      |                "dicts": [
      |                {
      |                  "name" : "period",
      |                  "db" : "dbp",
      |                  "proc" : "prm_salary.pkg_web_cons_rep_input_period_list(refcur => ?)"
      |                },
      |                  {
      |                    "name" : "position_group",
      |                    "db" : "dbp",
      |                    "proc" : "prm_salary.pkg_web_posit_rep_position_group_list(refcur => ?)",
      |                    "reftables" : ["prm_salary.sl_d_position_group" ]
      |                  },
      |                  {
      |                    "name" : "category_list",
      |                    "db" : "dbp",
      |                    "proc" : "prm_salary.pkg_web_cons_rep_edict_category_list(refcur => ?)",
      |                    "reftables" : ["prm_salary.sl_d_edict_category" ]
      |                  },
      |                  {
      |                    "name" : "oiv",
      |                    "db" : "dbp",
      |                    "proc" : "prm_salary.pkg_web_cons_rep_grbs_list(refcur => ?, p_user_id => 45224506)",
      |                    "reftables" : ["prm_salary.sl_user_access","prm_salary.sl_d_grbs","prm_salary.sl_f_cons_rep","prm_salary.sl_inst_grbs_link","prm_salary.sl_institution" ]
      |                  },
      |                {
      |                 "name" : "institution",
      |                  "db" : "dbp",
      |                  "proc" : "prm_salary.pkg_web_cons_rep_institution_list(refcur => ?, p_user_id => 45224506)",
      |                  "reftables" : ["prm_salary.sl_inst_grbs_link","prm_salary.sl_user_access","prm_salary.sl_d_institution_data_indication","prm_salary.sl_d_institution"]
      |                },
      |                {
      |                  "name" : "industry_class",
      |                  "db" : "dbp",
      |                  "proc":"prm_salary.pkg_web_cons_rep_form_type_list(refcur => ?)",
      |                  "reftables" : ["prm_salary.sl_f_cons_rep_hor","prm_salary.sl_d_industry_class"]
      |                },
      |                {
      |                  "name" : "territory",
      |                  "db" : "dbp",
      |                  "proc":"prm_salary.pkg_web_cons_rep_territory_list(refcur => ?)",
      |                  "reftables" : ["prm_salary.sl_f_cons_rep","prm_salary.sl_d_territory"]
      |                },
      |                {
      |                  "name" : "okfs",
      |                  "db" : "dbp",
      |                  "proc":"prm_salary.pkg_web_cons_rep_okfs_list(refcur => ?)",
      |                  "reftables" : ["prm_salary.sl_d_okfs"]
      |                }
      |              ]
      |             }
      |""".stripMargin




  val reqJsonText___ =
    """
      |              { "user_session" : "9d6iQk5LmtfpoYd78mmuHsajjaI2rbRh",
      |                "cont_encoding_gzip_enabled" : 0,
      |                 "thread_pool" : "block",
      |                 "request_timeout_ms": 5000,
      |                "dicts": [
      |                {
      |                 "name"  : "litener_notify_1",
      |                  "db"   : "dbp",
      |                  "proc" : "prm_salary.pkg_web_litener_notify(refcur => ?, p_user_id => 45224506)",
      |                  "reftables" : ["table1","table2"]
      |                },
      |                {
      |                 "name"  : "litener_notify_2",
      |                  "db"   : "dbp",
      |                  "proc" : "prm_salary.pkg_web_litener_notify(refcur => ?, p_user_id => 45224506)",
      |                  "reftables" : ["listener_notify","any_table_name"]
      |                },
      |                {
      |                 "name"  : "litener_notify_3",
      |                  "db"   : "dbp",
      |                  "proc" : "prm_salary.pkg_web_litener_notify(refcur => ?, p_user_id => 45224506)"
      |                }
      |              ]
      |             }
      |""".stripMargin

}
