"""Independent golden vectors for core:geo using geographiclib (C. Karney), not Vincenty."""
import json, random, sys
from geographiclib.geodesic import Geodesic
g = Geodesic.WGS84
random.seed(42)
cases = []
fixed = [((40.4168,-3.7038),(41.3874,2.1686)), ((51.5,-0.12),(40.71,-74.0)), ((-33.86,151.21),(-36.85,174.76)),
         ((0,179.5),(0,-179.5)), ((89.9,0),(89.9,180)), ((-89.0,10),(-88.0,-170)), ((39.95,4.13),(39.9505,4.1310))]
pairs = list(fixed)
for _ in range(60):
    pairs.append(((random.uniform(-85,85),random.uniform(-180,180)),(random.uniform(-85,85),random.uniform(-180,180))))
for (a,b) in pairs:
    r = g.Inverse(a[0],a[1],b[0],b[1])
    cases.append({"lat1":a[0],"lon1":a[1],"lat2":b[0],"lon2":b[1],"s12":r["s12"],"azi1":r["azi1"]%360,"azi2":r["azi2"]%360})
direct = []
for _ in range(40):
    lat=random.uniform(-80,80); lon=random.uniform(-180,180); az=random.uniform(0,360); s=random.choice([10,1000,50000,500000,5e6])
    r=g.Direct(lat,lon,az,s)
    direct.append({"lat1":lat,"lon1":lon,"azi1":az,"s12":s,"lat2":r["lat2"],"lon2":r["lon2"]})
anti = []
for _ in range(30):
    lat=random.uniform(-60,60); lon=random.uniform(-180,180)
    lat2=-lat+random.uniform(-0.8,0.8); lon2=((lon+180+random.uniform(-0.8,0.8)+180)%360)-180
    r=g.Inverse(lat,lon,lat2,lon2)
    anti.append({"lat1":lat,"lon1":lon,"lat2":lat2,"lon2":lon2,"s12":r["s12"],"azi1":r["azi1"]%360,"azi2":r["azi2"]%360})
json.dump({"source":"geographiclib %s WGS84"%__import__('geographiclib').__version__,"inverse":cases,"direct":direct,"antipodal":anti}, open(sys.argv[1],"w"), indent=1)
