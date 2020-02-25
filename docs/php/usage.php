..
..

   Service::trace(implode(";",$comb),
                 'After_getGrid_getHeader_combineCol');
         Service::trace($header, 'After_getGrid_getHeader_combineCol header');

        $table->combineRow($comb); 
         
        //=====================================================================
        $wsComboClient = new ComboClient(); 
        $allDicts = array();        
        try {
          $allDicts = $wsComboClient->loadDicts("http://127.0.0.1:8080/dicts",
                  $comb, $header->left_header); 
          
          Service::trace("ok", "ComboClient_loadDicts_Ok");
          Service::trace($allDicts, "allDict");   

          foreach ($comb as $code) {
            $table->setDirectory($code, $allDicts[$code]);
          }
        //=====================================================================
        } catch(Exception $wsExc){
        Service::trace($wsExc->getMessage(), 'ComboClient_loadDicts_exception');
            foreach ($comb as $code) {
                Service::trace("[".$code."][".implode(";",
                        $header->left_header[$code])."]", 
                        'table_setDirectory_loadDicts_BEFORE_2');
                $dict_array = self::loadDicts(
                        $header->left_header[$code], 
                        $settings->shortList, 
                        $settings->showInn);
                Service::trace("[".implode(";",$dict_array)."]", 
                        'DICT_FORMAT');
                $table->setDirectory($code, $dict_array);
            }
        }

        $rs = new Recordset('info_pg');
..
..