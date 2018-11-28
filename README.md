# BID-FACE-PLUGIN
BID Zoom SDK Cordova Plugin
	
	Este plugin provee fácil acceso al SDK Zoom  desde una aplicación Ionic usando la plataforma Android.

PREPARACIÓN DE LA APLICACIÓN.

	Antes que la aplicación pueda ser construida correctamente, la plataforma Android debe ser agregada mediante la ejecución: ionic cordova platform add Android desde linea de consola (CLI) mientras se ubica en el dorectorio raíz del proyecto.

INSTALACIÓN.

	Desde un proyecto existente de Ionic, ejecutar: ionic cordova plugin add plugins_src/cordova-plugin-face-bid-master (donde "plugins_src/cordova-plugin-face-bid-master" corresponde al directorio fuente del plugin).

INICIALIZACIÓN DE SDK.

	Se debe primero inicilizar el SDK con el token de la apliación antes de poder realizar el enrolamiento mediante el uso del SDK. El token sera proporcionado por BID. 

Token: dPDu0XcLyvAcTrdRs4fRkIfP4q3Bw7mP

ENROLAMIENTO DE USUARIO.

	Con el propósito de facilitar el enrolamiento, el plugin se encuentra diseñado para realizar el enrolamiento automaticamente una vez ejecutada la correcta.

var FaceBidEnrollment: any;
var appToken = "dPDu0XcLyvAcTrdRs4fRkIfP4q3Bw7mP";
var userID ="A unique user id";


FaceBidEnrollment.initialize(appToken, userID, onSuccess, onError);

        
IMPLEMENTACIÓN.

	Se recomienda evaluar la carga y estado de la plataforma antes de realizar el llamado al plugin, así como, la evaluación de la variable de acceso al plugin.
		
startFaceEnrollment(){
    this.platform.ready().then(() => {
      if (typeof FaceBidEnrollment !== 'undefined') {
		FaceBidEnrollment.initialize(appToken, userID, onSuccess, onError);
	  }
        else {
          alert("BID plugin not available.");
        }
    });
  }
  
	El manejo del éxito o falla del enrolamiento puede ser manejado a tráves de funciones typescript, mismas que son recibidas como parámetro en la implementación del plugin.
 
 CONSIDERACIONES.
 	
	El dispositivo debe encontrarse conectado a Internet.
 
