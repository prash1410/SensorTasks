<?php 
error_reporting(E_ALL ^ E_DEPRECATED);
if($_POST)
{
    $DeviceID = $_POST['DeviceID'];
}

$tableName = "device_";
$tableName = $tableName . $DeviceID;

$con=mysql_connect("localhost","root","");
mysql_select_db("acousticdatabase",$con);

$r=mysql_query("SELECT filename, filesize, timestamp FROM $tableName WHERE read_flag=0"); 
while($row=mysql_fetch_array($r)) 
{ 
	$out[]=$row;
}
mysql_query("UPDATE $tableName SET read_flag=1 WHERE read_flag=0"); 
print(json_encode($out));
mysql_close($con); 
?>