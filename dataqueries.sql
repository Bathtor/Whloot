SELECT solarSystemName FROM mapSolarSystems WHERE solarSystemName NOT REGEXP '^J[0-9|-]{6}' ORDER BY solarSystemName ASC;