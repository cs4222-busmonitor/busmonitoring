package busmonitoring.cs4222.nus.busmonitoring;

import java.io.Serializable;

public class BusDetails implements Serializable{

    public String driverName;
    public String vehicleNo;
    public String routeCode;

    public BusDetails(){
        driverName = "default_driver";
        vehicleNo = "default_vehicle";
        routeCode = "default_route";
    }

    public BusDetails(String dName, String vNo, String rCode){
        driverName = dName;
        vehicleNo = vNo;
        routeCode = rCode;
    }
}
