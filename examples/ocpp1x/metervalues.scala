send(
  MeterValuesReq(
    scope = ConnectorScope(1),
    transactionId = Option(transId),
    meters = List(
      meter.Meter(
        timestamp = ZonedDateTime.now(),
        values = List(
          meter.Value(
            value = "310",
            context = meter.ReadingContext.SamplePeriodic,
            format = meter.ValueFormat.Raw,
            measurand = meter.Measurand.EnergyActiveImportRegister,
            phase = None,
            location = meter.Location.Outlet,
            unit = meter.UnitOfMeasure.Kwh
          ),
          meter.Value(
            value = "6475.9",
            context = meter.ReadingContext.SamplePeriodic,
            format = meter.ValueFormat.Raw,
            measurand = meter.Measurand.PowerActiveImport,
            phase = None,
            location = meter.Location.Outlet,
            unit = meter.UnitOfMeasure.W
          )
        )
      )
    )
  )
)
