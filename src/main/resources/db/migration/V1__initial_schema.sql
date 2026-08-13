
--
-- Name: ledger_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ledger_entries (
    id character varying(255) NOT NULL,
    account character varying(255) NOT NULL,
    amount_paise bigint NOT NULL,
    created_at timestamp(6) with time zone,
    direction character varying(255) NOT NULL,
    payment_id character varying(255) NOT NULL,
    CONSTRAINT ledger_entries_direction_check CHECK (((direction)::text = ANY ((ARRAY['DEBIT'::character varying, 'CREDIT'::character varying])::text[])))
);


--
-- Name: merchants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.merchants (
    id character varying(255) NOT NULL,
    business_name character varying(255),
    created_at timestamp(6) with time zone,
    email character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    api_key character varying(255),
    password character varying(255),
    role character varying(255)
);


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    id character varying(255) NOT NULL,
    amount_paise bigint NOT NULL,
    created_at timestamp(6) with time zone,
    currency character varying(255) NOT NULL,
    merchant_id character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT payments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CAPTURED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: ledger_entries ledger_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT ledger_entries_pkey PRIMARY KEY (id);


--
-- Name: merchants merchants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merchants
    ADD CONSTRAINT merchants_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: merchants uk5j287t79f6on0o4o2toa1v848; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merchants
    ADD CONSTRAINT uk5j287t79f6on0o4o2toa1v848 UNIQUE (api_key);


--
-- Name: merchants ukgx9y1yah4qdijdi45ow7nxvdr; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merchants
    ADD CONSTRAINT ukgx9y1yah4qdijdi45ow7nxvdr UNIQUE (email);


--
-- PostgreSQL database dump complete
--


