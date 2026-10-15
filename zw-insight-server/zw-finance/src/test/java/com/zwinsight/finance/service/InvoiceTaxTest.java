package com.zwinsight.finance.service;

import com.zwinsight.finance.domain.BizInvoiceApply;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTaxTest {
    @Test void unknownTaxIsNotZero() {
        BizInvoiceApply invoice = new BizInvoiceApply();
        invoice.setInvoiceAmount(new BigDecimal("109"));
        assertThat(invoice.getTaxAmount()).isNull();
        invoice.setTaxRate(new BigDecimal("9"));
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("9");
        assertThat(invoice.getAmountWithoutTax()).isEqualByComparingTo("100");
        invoice.setTaxRate(new BigDecimal("-1"));
        assertThat(invoice.getTaxAmount()).isNull();
    }
}
