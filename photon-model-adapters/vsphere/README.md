# Getting the vSphere SDK
You need to download the vSphere SDK from here: https://developercenter.vmware.com/web/sdk/60/vsphere-management

After that navigate to the sdk/ folder and install the jars using maven passing the location of the VMware-vSphere-SDK-6.0.0-3634981.zip
file as system proeprty

```bash
mvn clean package -Dsdk.location=/path/to/VMware-vSphere-SDK-6.0.0.....zip
```

The sdk maven module installs the SDK jars into the local m2 registry and it will contain these artifacts:

```
.m2/repository/com/vmware/photon/vsphere
├── photon-vsphere-pbm
│   ├── 6.0.0-3634981
│   │   ├── photon-vsphere-pbm-6.0.0-3634981.jar
│   │   ├── photon-vsphere-pbm-6.0.0-3634981.pom
│   │   ├── photon-vsphere-pbm-6.0.0-3634981-sources.jar
│   └── maven-metadata-local.xml
├── photon-vsphere-sms
│   ├── 6.0.0-3634981
│   │   ├── photon-vsphere-sms-6.0.0-3634981.jar
│   │   ├── photon-vsphere-sms-6.0.0-3634981.pom
│   └── maven-metadata-local.xml
├── photon-vsphere-ssoclient
│   ├── 6.0.0-3634981
│   │   ├── photon-vsphere-ssoclient-6.0.0-3634981.jar
│   │   ├── photon-vsphere-ssoclient-6.0.0-3634981.pom
│   └── maven-metadata-local.xml
└── photon-vsphere-vim25
    ├── 6.0.0-3634981
    │   ├── photon-vsphere-vim25-6.0.0-3634981.jar
    │   ├── photon-vsphere-vim25-6.0.0-3634981.pom
    └── maven-metadata-local.xml

```

You may consider deploying these artifacts to your repository manager.