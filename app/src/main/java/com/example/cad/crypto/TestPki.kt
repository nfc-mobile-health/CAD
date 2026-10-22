package com.example.cad.crypto

object TestPki {
    val CA_CERT_PEM: String = """
        -----BEGIN CERTIFICATE-----
        MIIDHzCCAgegAwIBAgIUST/h3UbaM5gw9OFekk9tvT5KG/EwDQYJKoZIhvcNAQEL
        BQAwHzEdMBsGA1UEAwwUUy1NQVBMRSBUZXN0IFJvb3QgQ0EwHhcNMjYxMDIyMTU0
        OTI3WhcNMzYxMDE5MTU0OTI3WjAfMR0wGwYDVQQDDBRTLU1BUExFIFRlc3QgUm9v
        dCBDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALozg3s1M+fkhJLS
        hdczNl5uTwmOrNFVb/bD3y3fYpNyoBRyz+AN1Gmg9Y42srOJPFYt6caNal1hO0nt
        kNJanRIFFB+lGdpXFnOZqmp9rFJ+HrO7llIUUyTZ6+HzFST8hiSGR2m69nUhJNfO
        Iw/schSkaRtXHUYHXGbmttdxAqSqr2Z4PoT+ErCgY/UJWNwpa6y/NrfFe6+KMvzo
        P0xT65qiwOTW7uTSI9BpmQFptDYmni2a1WFiaFldfqJLlTTiYAcEmgxWycx7JMcq
        m9jvPWcOLIKqj8FryXRbE2fGo7kTaNNPTu4xF4eVw2pFwV9vBH1QuKHJdQ/Cu9zZ
        sauBzNUCAwEAAaNTMFEwHQYDVR0OBBYEFB1EcCanJckdEz3Gd6QWPrLQhIWmMB8G
        A1UdIwQYMBaAFB1EcCanJckdEz3Gd6QWPrLQhIWmMA8GA1UdEwEB/wQFMAMBAf8w
        DQYJKoZIhvcNAQELBQADggEBAAAFLuxZS2qFFzvanz3VtOMZ7RK1UgIE+5c0YdCj
        nQMpO12C4qlsp8InLUABg7Cks8f1f3+A4ZGecAR1uqbT1iFnHcVuueQaiQzZgyNh
        P5EsGADUHqFe5X9HOXNuLJLgKdGVHZ0ygepsnGZNPQVFxKVuNPEYphpvP475FZM0
        YWUz3WsFPtLOybcrGs8juwanQ6zldsIZXW5zqeTy2eTfRcOqHIdDfCeNaN1s/Unp
        JygL738sr5Qy8mocfP/l1FJGAjRdGF7bjvmKl+s4Ss77FClZUAuTYo7LuVWMr5NX
        ikbJXbRvu+0FjrSBf17gVV/wIzN6pE4aTbPQPKgstEu7cfI=
        -----END CERTIFICATE-----
    """.trimIndent()

    val CAD_CERT_PEM: String = """
        -----BEGIN CERTIFICATE-----
        MIIDCjCCAfKgAwIBAgIUW/AEHzoF/AB1rT+TxsqAqS+N28cwDQYJKoZIhvcNAQEL
        BQAwHzEdMBsGA1UEAwwUUy1NQVBMRSBUZXN0IFJvb3QgQ0EwHhcNMjYxMDIyMTU0
        OTI3WhcNMzYxMDE5MTU0OTI3WjAbMRkwFwYDVQQDDBBDQUQtUmVhZGVyLUhQMTAx
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyhmxFqpuSdvQelP9qPCo
        vkTJQQYAdPpLi88Xj6khnzGnI/Xt2WHnbIZKOV+3n1XxzV+SwHddbsurvVD9REnI
        dDfUq1oulyXxFBtCkvNDYInjIJ1xcbScd5tN0ralA86HcTSvRogSlF8YfOqEO6g8
        d36AYO1/56zezz/ECTnnedJDnHKm3potwTwqeLm8fSqtmUCPUrCqO4Iy/ZrJ41db
        fWn0zUELGPZVDIeWT8gsM085nA195vIZS1fWs+Eeww/u4Q4o/b+JA7PYHnQzGNNi
        r7Lzq+gg+dJILuTDUSv5n5QICQQVuvum+/Nry4z968ka8tw+JQ8McJcUbRh6hJlX
        dwIDAQABo0IwQDAdBgNVHQ4EFgQUEoqK0g7RfpbJEE1lKik2/Uupr/QwHwYDVR0j
        BBgwFoAUHURwJqclyR0TPcZ3pBY+stCEhaYwDQYJKoZIhvcNAQELBQADggEBADmn
        77T1n+YvS/q+r85kNPuhniAiVh9CGQ4MNF5aocUjf4Ng5tpU1DqR8DfPel65EizX
        ZY4g3nbB/Ike3fGuJGsk2AdyVxue/2Yye8CfHy68i8BJcwm6rrTeW+Rjyo7fNmbz
        9HxaPLOBXnOayLfblxIGL+lDSQTkJpIHFqnMvgz2pLSdOWIM2WC/hEF5XXMjeNTB
        6USplqjL/c7DWvSCllSQkNQHn+6Lh1692ZJEw7hGV9Hf3c9MraSbeCSm1hfZzEHq
        s6YuzTqFEB7zZPj+CGU0FIvBoFGUGagU322zBoj6ulnBSavosWHKWjvVQHXgLbf6
        5vLOERlj1KPyGbUK3EU=
        -----END CERTIFICATE-----
    """.trimIndent()

    val CAD_KEY_PEM: String = """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDKGbEWqm5J29B6
        U/2o8Ki+RMlBBgB0+kuLzxePqSGfMacj9e3ZYedshko5X7efVfHNX5LAd11uy6u9
        UP1ESch0N9SrWi6XJfEUG0KS80NgieMgnXFxtJx3m03StqUDzodxNK9GiBKUXxh8
        6oQ7qDx3foBg7X/nrN7PP8QJOed50kOccqbemi3BPCp4ubx9Kq2ZQI9SsKo7gjL9
        msnjV1t9afTNQQsY9lUMh5ZPyCwzTzmcDX3m8hlLV9az4R7DD+7hDij9v4kDs9ge
        dDMY02KvsvOr6CD50kgu5MNRK/mflAgJBBW6+6b782vLjP3ryRry3D4lDwxwlxRt
        GHqEmVd3AgMBAAECggEAIYobpFYla8wh2uNBzKh1Fe/RccvpWyscoCJUAoCVjAH6
        qdJK+5FZYt5zG/bBa50g6K3h619ZL1Aipg5LnGpy7K1FSyicuch6AUBi5INK3Q8V
        FUO7thyR/kbU+/wEAk/jP2DNw8fvmWr3Y+esM+sAGe6cUeRS8+OcWHWjIXWCxOmv
        HI+0x7mQxpe7f9FiRoNticMsgIzieoDx0oiMCNzYeoeOU5NxKGGH7OF+lT2CIAy2
        m5YO+dodNryZDsTLJC3HQ/bIbOlBgIM7DztUk4zCC8RW22aQB9h0W2CErJGthRJw
        8VxfP4DJ30PAQCqw5VqUhrE+uphxrkMWVIF4Ro7QGQKBgQD7t+FGll3gCs4oNJyn
        3MP12DJ7a7cnH0SzATDoYcGKN09UUDFHz1f9JbAu1P6IYiCIjh6drRoQEXAlAjm0
        ov8TnciaRzIFpFCD501Yv5wK2zK3Ju1LITDuLUldQop1yRHTmxLyUq0ZiJXRy4Dg
        VVXBz315TmJYYR0wcWe+0szAOQKBgQDNib99fXtCUhCT44SByPu51y/tNHSiHrSl
        4di14pZAs0pqb1w4Nhvy9FqcsV+VCbpJPwBa5xRLgQhXgiqNleC8Julb5QEMz5Av
        q4xeIo/FoQPQLHXYUeCUs5Yd/pHmSU2L84PSngHbnTt9u7wD6jPEkNBrK+iOonN2
        bLshVmh1LwKBgQCH/kUwTKa1F0FgCWSlcClS1hYYBg7yjPshtsAEnGPi15T9WUH2
        qPfBpa3071AAwPsQq6+wwke1bKZo6DXi+LjUkUdCyrokwCxbxwhLrWGRn/FbcCV9
        lJYJ59+eCggiMJp3920aDD7G77r5mHetO41UY1RHyGhrlTQe6vYA64SBSQKBgAwq
        VgowbiY7wz1tnv8rjTKvuOd6Tlj2Jk0XNAN3+Nq1gfcQxk2RblAQBHj3yqeAnuUM
        9JFs0feyGHNNggVFVLruNqu4vzop4uffaHx94TKaWCZvKz51gixrALyfv7T4q71X
        2XqS3ZtJECdeAD/fEddVlvBw9d/TX7AnuV+8xIwDAoGBAIEzfYkIuTpau8n/IvHE
        MtnUnbun6JhTOYCAgQH+CFNNlPUoq4JIMFIEynX4Uf8pKdlEBCnDcIUxWbN1uHBi
        J40NZH1zXV3Zy9c4TRQOLa+B2N7sEI0HZOEfwes/17vQrAAcmaE8OHQbjiwwwrU/
        k/WUfo92Oms+gau7snVICCj3
        -----END PRIVATE KEY-----
    """.trimIndent()

    val MCARD_CERT_PEM: String = """
        -----BEGIN CERTIFICATE-----
        MIIDBjCCAe6gAwIBAgIUW/AEHzoF/AB1rT+TxsqAqS+N28gwDQYJKoZIhvcNAQEL
        BQAwHzEdMBsGA1UEAwwUUy1NQVBMRSBUZXN0IFJvb3QgQ0EwHhcNMjYxMDIyMTU0
        OTI4WhcNMzYxMDE5MTU0OTI4WjAXMRUwEwYDVQQDDAxtQ2FyZC1BVjIwNDEwggEi
        MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC/fhdjEMld3jiUyb5qLJoC47Zw
        zRPfbUUlvNlfCHRNSt0LcM0pH/boP2Nauldmc84RHjQdAaJomSrAWMIRIr+cyqGz
        lBwiTUpSUyjrLR1ZQOcOKXhscNhkVsuMlJvg2Ur8uWdzb7tAL91wokjeHtyWC6Cf
        CappF5sWbhpbGF8p03gx6bvuAWzl6SApjJuur9irvfICALcm18JgPq7Hxn5DxWfK
        PwcxadqeT9j4WOdqwHm5aGKjOYHG2oJrXgwo6v8BKVkfmCaj5UPx4ZmYwxMmRqwq
        NuGv6PbGek+haYJMRTC/2NoS+3UO7o+NWHSsJLCv7LtpAIjyzf3L8OQBBPDvAgMB
        AAGjQjBAMB0GA1UdDgQWBBQ209cDxt9Hy0oB3qOyIbPN5PXWDTAfBgNVHSMEGDAW
        gBQdRHAmpyXJHRM9xnekFj6y0ISFpjANBgkqhkiG9w0BAQsFAAOCAQEAlvEOwVqa
        OEbQcnCB+kJG3SFqGYXDQ6dvt+aAeLHllvQEnfIQHKUKjU42g1Yvp6sfn4eEfikf
        Km94XuwlfTLkHD7fqvwnsO+Z2+POGEJ7bzoADol8/lUirXRCPHf/13mntrThxEoH
        y6nbG7FbdFvJqTvuhfIkep2J5VuTY1Qg3VWt2GVnEPT2dQAO+5cLnJdeJbMIaYqW
        p38ubVe/CwbmVy8n/gr7yRZTLoVwpCgbqxMpFtSPtnu08tWE4JhFlHZYUNuV9kwX
        vIcN0k1aju7TGYSUBrvrQAV2aPYbxaPg5I7SJfs5oQVgJ8bZuOcsSAEg2+mCGFjG
        5YRSSbQHWCPzUg==
        -----END CERTIFICATE-----
    """.trimIndent()

    val MCARD_KEY_PEM: String = """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC/fhdjEMld3jiU
        yb5qLJoC47ZwzRPfbUUlvNlfCHRNSt0LcM0pH/boP2Nauldmc84RHjQdAaJomSrA
        WMIRIr+cyqGzlBwiTUpSUyjrLR1ZQOcOKXhscNhkVsuMlJvg2Ur8uWdzb7tAL91w
        okjeHtyWC6CfCappF5sWbhpbGF8p03gx6bvuAWzl6SApjJuur9irvfICALcm18Jg
        Pq7Hxn5DxWfKPwcxadqeT9j4WOdqwHm5aGKjOYHG2oJrXgwo6v8BKVkfmCaj5UPx
        4ZmYwxMmRqwqNuGv6PbGek+haYJMRTC/2NoS+3UO7o+NWHSsJLCv7LtpAIjyzf3L
        8OQBBPDvAgMBAAECggEAEGeufDuvbJ3BjimcaP8qMI4ujopbJfx3/IwpuSUBxa9g
        sX637MqCvhHUIq0tGKdaI4hJYaTGs5xPl8vi0FIARV/sq3RIVr31XIkoS8GBLqjL
        QqDV4sBmIW54ILKIiiJDdNQzYyXSzmRrXZ9hi7nsHuSfHZDX6wntLOZA1sYHPQ9t
        Axg+DawSQyy+NR4xlr1gS16mkeZFnKwKNHeOfI6ogBCxl3ugM4/UbLvTLEj1WtTL
        YsSSfArNgVXL1vKWl3rUiHYm9N/X97Tt9FAxG2BauKF/nUz+Sr8w47yWpFq9mx4m
        D9+r1nXbxo85uzbA5tHGmxh46aFNbVM4T477uvw6MQKBgQD+TYgsN2jmHU/gGkO0
        uMqG6sDu+Un4koZlZOXfOsfxPL+OCW54nnHX93WOEvVJOda61ClTdFXXjx23RfkD
        ZjcCne11pI8gz2XoBNII3i784zphXNzTcP8+az8tDXzRfyUtTashKK+la8I1T9dQ
        aQFo06mRHgygFazk7sNikaAQ/wKBgQDAxUAEwz455ap9sHjQfBBzqo63o23V54Yl
        fav6+dlF+nPGo+tnmoN3jr5kVW+UoB2OT1PuTZXUeqizT5jl7ARNlMnhBGcp03QW
        57JTIA376HA0g8cA67Nc/B5HN41OHVzgUCc3GMXFLULozLM5TTTciGJxn5ekn4t9
        Xj9MOcwwEQKBgQCGr12NvZMLPT8OvBpb3vs01aR1bQamZkRT80pkHmfC9p+lhI1D
        yPcWoPNb2eKbc8ysiq2Qi67jSeM9LAwkpsA/3JDlm9BKWk3owpTTKYnNWjOTWXGD
        cvdcgcSmAdy3wBValyYpm7jbbeUgAzdQWSzr7h6Cw12Ac7psTaCh1erm1QKBgQCS
        cYbX12cfXr0tbPHrGET7SdfP5ETdmlxLgPQ03rfTtJddkUuVUkmLexJicJEqrJSn
        dtZBFAm9+EzOo6p5CNqgAWd4HPsuSChJ+kx7X7+f3o3XJ0D4DxccjZ+G5MnhTOcw
        tVOFRLmBuIe7CT8iSefdnXKD9QSjmFzEqeOWSdY3sQKBgA6ELH4iALoTulUouDT0
        lkq8jzZPZbiydlIZ1ph2KSCx+j8q3MZyV8fOiGfxgc58qRQr3MfNt8thZi4HKRMR
        Qy+jrG/cmBu2HPfOp4S8aKyW/CZeNPefCdu7kERksxvWU3Jc2Hy2lpXEB2P2DXr+
        ki1MPSWAwsL1YHqV5Nlg8O7j
        -----END PRIVATE KEY-----
    """.trimIndent()

    val RootCaCertPem: String get() = CA_CERT_PEM
    val CadCertPem: String get() = CAD_CERT_PEM
    val CadPrivateKeyPem: String get() = CAD_KEY_PEM
    val CardCertPem: String get() = MCARD_CERT_PEM
    val CardPrivateKeyPem: String get() = MCARD_KEY_PEM

    val CadCertDer: ByteArray by lazy {
        CadCrypto.parseCertificate(CAD_CERT_PEM).encoded
    }
    val CardCertDer: ByteArray by lazy {
        CadCrypto.parseCertificate(MCARD_CERT_PEM).encoded
    }
}
