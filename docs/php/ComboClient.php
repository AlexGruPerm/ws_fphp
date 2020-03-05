<?php

/**
 * Description of ComboClient:
 * 
 * Web service client for getting dictionaries (combo) from db
 * in parallel maner with single call.
 * 
 * Build the single json with dictionaries description and send as POST
 * data to web service.
 *
 * @author Yakushev A.N. 17.01.2020
 * 
 */
class ComboClient {
    
    private $common_debug = "LD:";//todo: remove a lot of trace messages.
    private $srv;
    
    public function __construct() {
        Service::trace("__construct", 'ComboClient');
        $this->$srv = new Service();
    }

    private function HTTPPost($url, $combCode, array $params) {
        Service::trace("Begin", $common_debug.'HTTPPost');
        $postVariables = json_encode($params);
        Service::trace($postVariables, $common_debug.'HTTPPost.postVariables');

        $headers = array();
        $headers[] = 'Accept: application/json'; 
        $headers[] = 'Accept-Encoding: gzip';
        $headers[] = 'Content-Length:'.strlen($postVariables);
        $headers[] = 'Content-Type: application/json; charset=utf-8';
        $headers[] = 'Connection: keep-alive'; 
        $headers[] = 'Pragma: no-cache'; 
        $headers[] = 'Cache-Control: no-cache';     

        $ch    = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $postVariables);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        //tell cURL to decode the response automatically (gzip)
        curl_setopt($ch, CURLOPT_ENCODING, "");
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 30);
        curl_setopt($ch, CURLOPT_HEADER, 0);
        $response = curl_exec($ch);
        curl_close($ch);

        //$result1251 = iconv("UTF-8", "CP1251", $response);

        //json_decode only works with UTF-8 encoded strings.
        $resJson = json_decode($response); //$resJson is object, not an array
        $resultStatus = $resJson->status;
        //$resultData = $resJson->data;
        $resultDicts = $resJson->data->dicts; //todo: replace $resultData with $resJson->data 

        Service::trace($resultStatus, $common_debug.'HTTPPost COMMON Status');
        $combinedRes = array();
        if (isset($resultStatus) and $resultStatus == 'ok'){
            Service::trace("WS result status = [$resultStatus]", 'WS CHECK');
            /*
             * $combCode:
             *  oiv       => ID_COL_1
             *  territory => ID_COL_2 
             *  okfs      => ID_COL_3
            */
            foreach($resultDicts as $DictKey => $value) {
              if ($resultDicts[$DictKey]->name == 'edict_cat') {
                $tmpRows = array();
                foreach($resultDicts[$DictKey]->rows as $row){
                  $singleRow = array();
                  foreach($row as $cell){
                    $singleRow[$cell->name] = $cell->value;  
                  }
                  // id = 1 name = "�� - �����" 
                  $tmpRows[$singleRow['id'].'_ID_COL_1'] = 
                          iconv("UTF-8", "CP1251", $singleRow['name']).'***';      
                } 
                $combinedRes[$combCode[$resultDicts[$DictKey]->name]]=$tmpRows;
              }
            }
   
        /*
                  $res = array();
        while ($rs->movenext()) {
            if ($el['CODE'] == 'industry_class') {
              $res[$rs->fields['ID'] . '_' . $el['ID']] = $rs->fields[$data['fullField']];
            } elseif ($el['CODE'] == 'institution' && $showInn) {
              $res[$rs->fields['ID'] . '_' . $el['ID']] = $rs->fields[$data['field']] . ' (' . $rs->fields['INSTITUTION_INN'] . ')';
            } elseif ($short) {
              $res[$rs->fields['ID'] . '_' . $el['ID']] = $rs->fields[$data['shortField']];                
            } else {
              $res[$rs->fields['ID'] . '_' . $el['ID']] = $rs->fields[$data['field']];
            }   
        }

        $res[-1] = '�����';
        */

        } else {
           throw new Exception("Service not available."." Error: error message.");
        }
       return $combinedRes; 
    }
 
    
    /**
    * Process input array with dictionaries meta information
    * and request web service that execute db queries in parallel.
    *
    * @param $comb fixed dimensions of report table: 
     * ID_COL_1; ID_COL_2; ID_COL_3 
     *  
    * @param $left_header instance of class Header
    *     {ID_COL_1;0;1;ID_COL_1;tratra;text;krai_oiv;������� ����},
    *     {ID_COL_2;0;1;ID_COL_2;tratra;text;industry_class;�������/����������}
     * 
    * @return array of dictionaries, like :
     * 
     * 
    */  
    public function loadDicts($wsurl, $comb, $left_headers) {
     $common_postfix ="=====================================================";
     Service::trace("Begin function ", 
             $common_debug."loadDicts ".$common_postfix);
     
     /* Array of arrays, 
      * array (
            array(
                   "name" => "oiv",
                   "db" => "db2_msk_gp",
                   "proc" => "prm_salary.pkg_web_cons_rep_form_type_list(refcur => ?)"
            ),
            array(
                   "name" => "okfs",
                   "db" => "db2_msk_gp",
                   "proc" => "prm_salary.pkg_web_cons_rep_okved_list(refcur => ?)"
                 )   
         ) 
     */
     $list_requested_dicts = array();
     //$combCodeArray = array();
     foreach($comb as $combCode){
       $thisDict = array();
       $thisCombCode = array();
       $thisDictCode = $left_headers[$combCode]['CODE'];
       $data = Header::$sql_dict[$thisDictCode];
       $thisDict['name'] = $thisDictCode;
       $thisDict['db'] = 'db1';
       $thisDict['proc'] = $data['sql'].'(refcur => ?'.($data['bind'] ? ', p_user_id => '.User::$id : '').')';
       array_push($list_requested_dicts, $thisDict);
       $combCodeArray[$thisDictCode] = $combCode; 
       // [{k=territory,v=ID_COL_1}, {k=oiv,v=ID_COL_2}]
     }
          
     $reqDictsArray = array(
        "user_session" => Auth::$session,
        "cont_encoding_gzip_enabled" => 1,
        "thread_pool" => "block",
        "request_timeout_ms" => 5000,
        "cache_live_time" => 120000,
        "dicts" => $list_requested_dicts);
     
     $wsResDicts = $this->HTTPPost($wsurl, $combCodeArray, $reqDictsArray);
     Service::trace("End function ", 
             $common_debug."loadDicts ".$common_postfix);
     
     //throw new Exception('Service not available. Error : error message.');
     
     return $wsResDicts;
    }

}
