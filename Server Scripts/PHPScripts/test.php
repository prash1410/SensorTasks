<?php 
$file_path = "acoustic_data/";
$sent_data = explode(';',basename( $_FILES['uploaded_file']['name']));
$file_path = $file_path . $sent_data[0];
$file_name = $sent_data[0];
$device_ID = $sent_data[1];
$latitude = $sent_data[2];
$longitude = $sent_data[3];
if(move_uploaded_file($_FILES['uploaded_file']['tmp_name'], $file_path)) 
{
	$pyscript = 'C:\\wamp64\\www\\PythonScripts\\test.py';
	$python = 'C:\\ProgramData\\Anaconda3\\python.exe';
	$cmd = "$python $pyscript $file_name $device_ID $latitude $longitude";
	exec($cmd, $output, $ret_code);
} 
?>


